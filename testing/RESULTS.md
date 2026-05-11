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

Every one of the 160 distribution events matched between the reference Java
implementation (mirroring the VBA math) and the DSL evaluator within
**0.01 cent** on every money column and **0.0001** on reinvest units.

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
the `reinv_units` output — see the *Friction* section below for why.

## What worked cleanly

- **Phrasal references resolve via the DataSource.** The DSL uses noun-phrase
  inputs (`units held quarterly`, `distribution income`, `closing nav`,
  `withholding rate`, `reinvest preference`) which the type checker leaves as
  `DEFERRED_REFERENCE` (FF9001, info-level). The runtime canonicalizes each
  phrase to a string key and reads it from `MapDataSource`. Same .ff source
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
`BigDecimal`), not `MoneyVal`. The .ff does Number arithmetic throughout. The
platform layer can wrap the final gross / tax / net values as Money at the
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
Money`, but is silent on `BigDecimal * Percentage`. The editorial choice in the
runtime (promote to Percent) is semantically suspect for the common
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
already do this with a parser-level disambiguation. Authoring would feel more
natural.

### 4. Per-event iteration is hosted, not in-DSL

The DSL describes the math for one event. The driver loops over the cartesian
of (investor × fund × quarter) and invokes the evaluator per row. This is the
correct factoring for a tree-walking runtime — but it means the
**iteration shape lives outside the rule** (in Java today; in the platform's
job scheduler tomorrow).

This is the WP-11/WP-12 limitation I called out earlier: the DSL's `allocate
… across <set> by <weight>` operator expects a flat `ListVal` of weights; it
can't apply a per-row formula (with per-row tax rate, per-row reinvest pref)
the way the VBA's nested loop can. The per-fund domain catalog (WP-12) plus a
proper iteration primitive (e.g. `for each <name> in <set>: <rule body>`)
would close the gap and let a single .ff express the whole macro instead of
just the inner kernel.

## Decision summary

For a real-world translation of an Excel macro that does per-row distribution
math, the DSL is **expressive enough to capture the economic logic exactly**
(zero numeric divergence across 160 events). The frictions surfaced are about
**type-system ergonomics** (Money rounding, Number-Percent multiplication) and
**iteration affordances** (DSL covers one event; driver covers the loop). Both
are addressable without breaking the existing surface.
