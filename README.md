# FundFlow DSL

A domain-specific language for fund accounting — reads like a fund's offering
documents, runs deterministically, ships with a parser, AST, semantic analyzer,
tree-walking interpreter, language-server, formatter, command-line tools, and an
AI-agent integration that generates valid programs from natural-language prompts.

```fundflow
rule "Management Fee Daily Accrual" {
  description: "1.5% per annum on opening NAV, accrued daily"
  applies to: all share classes of fund "Alpha Master Fund LP"
  effective: from 2026-01-01

  let rate     = 1.5% per annum
  let basis    = opening nav of share class
  let day_count = actual/365

  accrue rate on basis using day_count
  post to ledger account "Management Fee Payable"
    with narrative "Daily mgmt fee accrual"
}
```

## What's in the box

| Module | Responsibility |
|---|---|
| `core/` | Domain types: `Money`, `BusinessDate`, `Period`, `Percentage`, `DayCount`, `Quantity`, Fund/ShareClass/Investor/NAV/LedgerAccount records |
| `ast/` | Sealed AST hierarchy; minimal pretty-printer used by the formatter and round-trip tests |
| `parser/` | ANTLR4 grammar (`grammar/`), parser test corpus (60 valid + 60 invalid programs), AST builder |
| `stdlib/` | 32 functions across math / stats / financial / date categories (`abs`, `round`, `npv`, `irr`, `xirr`, `weighted average`, `edate`, …) |
| `semantic/` | Symbol table, type checker (§6.2 rules), effectivity validator, source-map, Levenshtein "did you mean" |
| `runtime/` | Tree-walking interpreter; `Money` arithmetic with currency checks; deterministic audit trail (SHA-256) |
| `diagnostics/` | Rust-style diagnostic renderer (caret + gutter + hint) and the source-code formatter |
| `lsp/` | LSP4J language server: `didOpen` / `didChange` diagnostics, `formatting`, `hover`, `completion`, `definition` |
| `cli/` | `fundflow` command with subcommands `parse` / `check` / `run` / `format` / `lsp` / `catalog` / `agent` |
| `agent/` | Anthropic Claude integration: validate-and-retry loop that generates `.ff` programs from natural language |
| `examples/` | Six canonical examples (§9.1–9.6 of the spec): mgmt fee, perf fee with HWM, capital call, NAV, equalization, European waterfall |
| `testing/` | Real-world end-to-end translation of an Excel VBA distribution engine into the DSL (see below) |

640+ tests across the reactor. `mvn verify` runs them all in ~16s.

## Quick start

```bash
# Build everything
mvn -B install

# Parse / check / format an example
java -jar cli/target/fundflow-dsl-cli-0.1.0-SNAPSHOT-cli.jar check examples/01_management_fee.ff

# Run an example with a fixture
echo "opening nav of share class = USD 10000000" > /tmp/data.fixture
java -jar cli/target/fundflow-dsl-cli-0.1.0-SNAPSHOT-cli.jar run \
    examples/01_management_fee.ff --as-of 2026-03-15 --fixture /tmp/data.fixture
```

```
# postings (1)
  2026-03-15  ledger account "Management Fee Payable"  USD 410.96  // Daily mgmt fee accrual

# audit trail: 5 entries; hash=04ab5bf46d88...
```

## AI agent integration

`fundflow agent --prompt "..."` takes a natural-language request and generates a
validated `.ff` program. The agent is forbidden from inventing identifiers — every
reference must resolve against the supplied catalog (`fundflow catalog [file]`).

Requires `ANTHROPIC_API_KEY` (or the alias `ANTROPIC_KEY`) in the environment or
in a `.env` file in the working directory. The agent uses `claude-opus-4-7` with
adaptive thinking and prompt caching on the system prompt.

```bash
# Verified live: 5.1s, accepted on attempt 1
FUNDFLOW_LIVE_TESTS=1 mvn -B -am -pl agent test \
    -Dtest=AnthropicAgentLiveTest -Dsurefire.failIfNoSpecifiedTests=false
```

## Spec

The full language and tooling specification lives at
[`requirements/FundFlow-DSL-Spec-v0.1.md`](requirements/FundFlow-DSL-Spec-v0.1.md).
Twelve work packages (WP-0 setup → WP-12 AI agent) — all implemented.

Supporting docs:

- [`docs/core-types-cheatsheet.md`](docs/core-types-cheatsheet.md) — domain types reference
- [`docs/language-guide.md`](docs/language-guide.md) — DSL surface syntax with examples
- [`docs/diagnostics.md`](docs/diagnostics.md) — every `FFxxxx` diagnostic code

---

# End-to-end test: VBA distribution engine → FundFlow DSL

A real-world translation of `FundDistributionEngine.xlsm`'s `RunDistributionCalculation`
macro into FundFlow DSL, with a row-by-row numerical comparison to prove the DSL
captures the VBA's economic logic exactly.

## What got translated

| VBA artifact | FundFlow DSL artifact |
|---|---|
| `DistributionEngine.bas` — `RunDistributionCalculation` (per-event math) | `testing/distribution_event.ff` — one `rule` with 4 `let` bindings and 4 `publish` |
| `Funds!`, `Investors!`, `Tax_Rates!` sheets | `testing/{funds,periods,investors,holdings,tax_rates}.csv` (extracted from the .xlsm) |
| VBA iteration over holdings × quarters | Java driver loops, calls `Evaluator.evaluate()` once per `(investor, fund, quarter)` |
| `Distributions!` sheet output | Reference Java impl outputs + DSL outputs, compared row-by-row |
| `GenerateSummaryReport` rollup | Aggregated grand totals printed in test stdout (gross / tax / net) |

## Result

```
Distribution engine end-to-end:
  events compared:   160
  total gross (USD): 216929.20
  total tax  (USD):  25367.55
  total net  (USD):  191561.65
```

Every one of the **160 distribution events** matched between the reference Java
implementation (mirroring the VBA math) and the DSL evaluator within **0.01 cent**
on every money column and **0.0001** on reinvest units.

Run it yourself:

```bash
mvn -B -am -pl parser test -Dtest=FundDistributionEngineTest \
    -Dsurefire.failIfNoSpecifiedTests=false
```

## How the math maps

The VBA inner loop (lines 121–128 of `DistributionEngine.bas`):

```vba
Dim gross As Double: gross = u * distPerUnit
Dim tax As Double: tax = gross * rate
Dim net As Double: net = gross - tax
Dim reinvUnits As Double: reinvUnits = 0
If pref = "Reinvest" And nav > 0 Then
    reinvUnits = net / nav
End If
```

becomes 4 `let` bindings + 4 `publish` statements in the DSL:

```fundflow
let gross       = u * dpu
let tax         = gross * rate
let net         = gross - tax
let reinv_units = net / nav

publish gross
publish tax
publish net
publish reinv_units
```

The driver applies the `pref = "Reinvest"` gate outside the DSL when consuming
the `reinv_units` output — see *Friction* below for why.

## What worked cleanly

- **Phrasal references resolve via the DataSource.** The DSL uses noun-phrase
  inputs (`units held quarterly`, `distribution income`, `closing nav`,
  `withholding rate`, `reinvest preference`) which the type checker leaves as
  `DEFERRED_REFERENCE` (FF9001, info-level). The runtime canonicalizes each
  phrase to a string key and reads it from `MapDataSource`. Same `.ff` source
  parses, type-checks, and evaluates without code changes.
- **Per-event evaluation is fast.** 160 evaluator runs complete in well under
  100ms total; the spec's 500ms-per-validation target has a 1000× margin here.
- **Number arithmetic carries exact precision.** All math runs at
  `MathContext.DECIMAL64`; the reference and the DSL produce byte-identical
  `BigDecimal` values on every column.

## What surfaced as friction (worth feeding back into the language)

### 1. `Money`'s eager rounding clashes with per-unit rates

The VBA stores `dpu = 145000 / 1250000 = 0.116` as a double. The DSL's `Money`
type normalizes amounts to currency fraction digits **at construction time**
(`0.116 USD → 0.12 USD`), which corrupts per-unit prices and per-unit
distribution rates immediately.

**Workaround used here:** the test passes DPU and NAV as `NumberVal` (raw
`BigDecimal`), not `MoneyVal`. The `.ff` does Number arithmetic throughout.
The platform layer can wrap final gross / tax / net values as Money at the
output boundary if it wants currency formatting — but the per-event math
stays exact.

**Cleaner fix for the language:** extend `Money` with a high-precision mode
(or introduce a separate `Price` type with 4–6 decimal places). Fund accounting
systems routinely need both: a 2-dp "settlement amount" and a 4–6-dp
"per-unit price." `Money(value, currency, scale)` with scale defaulting to
`currency.getDefaultFractionDigits()` would close this.

### 2. `Number * Percentage` returns `Percentage`, breaks `Number - Percentage`

If `gross` is `NumberVal` and `rate` is `PercentVal`, then `gross * rate`
returns `PercentVal` (under `Arithmetic.multiply`). The next line
`net = gross - tax` then crosses types (`Number - Percent`), which
`Arithmetic.subtract` rejects with an `EvaluationException`.

**Workaround used here:** pass the tax rate as `NumberVal` (the raw ratio:
`0.10`, `0.075`, `0.20`) rather than `PercentVal`. Keeps the math homogeneous
in `Number` space.

**Cleaner fix for the language:** the runtime rule could treat
`Number * Percentage` as `Number * ratio = Number` rather than promoting the
result to `Percentage`. Spec §6.2 explicitly defines `BigDecimal * Money →
Money`, but is silent on `BigDecimal * Percentage`. The editorial choice in
the runtime (promote to Percent) is semantically suspect for the common
"apply a tax/fee rate to a quantity" pattern.

### 3. `per` is a reserved word, blocking obvious noun phrases

The most natural names — `distribution per unit`, `nav per unit`,
`tax per fund strategy` — would conflict with the `per annum` keyword pair
in the lexer. The current DSL handles `per annum` as a multi-word token, so
`per` alone is reserved.

**Workaround used here:** rename to single-noun phrases that don't use `per`
(`distribution income`, `closing nav`, `withholding rate`).

**Cleaner fix for the language:** make `per` a contextual keyword (only
treated specially when immediately followed by `annum`). The grammar can
already do this with a parser-level disambiguation. Authoring would feel
more natural.

### 4. Per-event iteration is hosted, not in-DSL

The DSL describes the math for one event. The driver loops over the cartesian
of `(investor × fund × quarter)` and invokes the evaluator per row. This is the
correct factoring for a tree-walking runtime — but it means the
**iteration shape lives outside the rule** (in Java today; in the platform's
job scheduler tomorrow).

This is a known WP-11/WP-12 limitation: the DSL's `allocate … across <set> by
<weight>` operator expects a flat `ListVal` of weights; it can't apply a
per-row formula (with per-row tax rate, per-row reinvest pref) the way the
VBA's nested loop can. A per-fund domain catalog (WP-12) plus a proper
iteration primitive (e.g. `for each <name> in <set>: <rule body>`) would
close the gap and let a single `.ff` express the whole macro instead of just
the inner kernel.

## Decision summary

For a real-world translation of an Excel macro that does per-row distribution
math, the DSL is **expressive enough to capture the economic logic exactly**
(zero numeric divergence across 160 events). The frictions surfaced are
about **type-system ergonomics** (Money rounding, Number-Percent multiplication)
and **iteration affordances** (DSL covers one event; driver covers the loop).
Both are addressable without breaking the existing surface.
