# FundFlow DSL — Language Guide

This guide covers the DSL surface from a fund accountant's point of view. Each section pairs the **shape** of the construct (what you write) with its **runtime semantic** (what the engine does). Type rules are in §4 of the spec; diagnostic codes live in [`docs/diagnostics.md`](diagnostics.md).

## 1. Programs

A `.ff` file may begin with an optional `module` declaration followed by zero or more `import`s, then one or more top-level declarations:

```fundflow
module fundflow.examples.alpha
import fundflow.stdlib.fees

rule "..."     { ... }
schedule "..." { ... }
waterfall "...' { ... }
policy "..."   { ... }
type extension Foo extends Fund { ... }
```

Comments: `//` to end-of-line, `/* … */` block. Whitespace is insignificant. Keywords are case-insensitive; quoted identifiers are case-sensitive.

## 2. Rules and clauses

A rule has a name (quoted string) and a body of clauses. Clauses can appear in any order:

```fundflow
rule "Daily NAV Strike" {
  description: "..."                 // documentation, optional
  applies to: <selector>             // limits scope, optional
  effective:  <period>               // when this rule is active, optional
  let <name> = <expression>          // any number of bindings
  <statement>                        // any number of statements
}
```

`schedule`, `policy` blocks accept the same clauses. `waterfall` blocks accept `let`-bindings and statements only.

## 3. Operators

Each operator below has a **surface syntax** (what users write) and a **semantic** (what runs).

### 3.1 `let` — name a value

```fundflow
let rate     = 1.5% per annum
let basis    = opening nav of share class
let day_count = actual/365
```

Binds `<name>` in the rule's local scope. References to `<name>` later in the rule resolve to this binding before falling back to the data source.

### 3.2 `accrue … on … using …` — compute a daily accrual

**Surface:**
```fundflow
accrue <rate> on <basis> using <day_count>
```

**Semantic:** evaluates `basis * rate * day_count.yearFraction(asOf - 1, asOf)` and stores the result as the rule's *current accrual*. A subsequent bare `post` (no subject) consumes this value.

The window is one day from `asOf - 1` to `asOf`. To accrue over an arbitrary period, multiply explicitly with `over … using …` (see §3.7) and bind to a `let`.

### 3.3 `allocate … across … by …` / `equally` — split an amount

**Surface:**
```fundflow
allocate <amount> across <set> by <weight>
allocate <amount> across <set> equally
```

**Semantic:** distributes `<amount>` across the elements of `<set>`.

- **Pro-rata:** each element receives `amount * weight_i / sum(weights)`. Rounding residual is absorbed by the last element so the sum-of-allocations exactly equals `<amount>`.
- **Equally:** each element receives `amount / n`. Rounding residual goes to the last element.
- If `<set>` evaluates to an empty list or all-zero weights, the engine falls back to an equal split.

The result is stored as the rule's *current allocations*. A subsequent `post each allocation to <account>` iterates them.

> **Today's limitation.** `<set>` must resolve to a `ListVal` of numeric weights directly. True per-investor scope (where `by basis` evaluates `basis` against each investor record) needs the per-fund domain catalog from WP-12.

### 3.4 `distribute … through waterfall …` — run a named waterfall

**Surface:**
```fundflow
distribute <amount> through waterfall "<name>"
```

**Semantic:** looks up the named `waterfall` block and runs its body (let-bindings and statements) with `distributable` bound to `<amount>`. Each statement may post to ledger accounts. If the name doesn't resolve, the engine logs a `waterfall not found` audit entry and continues.

### 3.5 `post … to … with narrative …` — write a ledger entry

**Surface:**
```fundflow
post <amount> to ledger account "<name>" with narrative "<text>"
post to ledger account "<name>" with narrative "<text>"   // bare form
post each allocation to ledger account "<name>"           // iterates last allocations
```

**Semantic:** appends a `LedgerEntry(asOf, account, amount, narrative, sourceRule)` to the run's postings. The bare form (no subject) uses the rule's current accrual. The `each allocation` form posts every entry from the rule's current allocations.

### 3.6 `publish <expression>` — record an output

**Surface:**
```fundflow
publish nav as of valuation date
publish fee
```

**Semantic:** stores the evaluated value in `outputs[<rule>:<expression>]`.

### 3.7 `<expression> over <period> using <day_count>` — apply a year-fraction multiplier

**Surface:**
```fundflow
let prorated_fee = annual_fee over from 2026-01-01 to 2026-04-01 using actual/365
```

**Semantic:** multiplies the inner expression by `day_count.yearFraction(period.start, period.end)`. Inner Money/Percentage/Number are scaled in place. If the period boundaries can't be resolved to literal dates, the operator passes the inner expression through unchanged.

### 3.8 `<expression> as of <date>` — historical evaluation

**Surface:**
```fundflow
let q1_nav = nav as of 2026-03-31
```

**Semantic:** evaluates the inner expression with `ctx.asOf` shifted to `<date>`. Phrasal references inside (e.g., `nav`, `position market value`) resolve via `DataSource.lookupAsOf(name, date)`. The shift is restored on the way out — subsequent postings still use the rule's outer asOf.

### 3.9 `<expression> at start/end of <period>` — period-boundary lookup

**Surface:**
```fundflow
let opening = nav at start of period
let closing = nav at end of period
```

**Semantic:** today this passes through to the inner expression. The period boundary is meaningful only when paired with a phrasal reference resolved via the per-fund catalog — full handling lands alongside that catalog (WP-12).

### 3.10 `<expression> per annum` — rate annualization marker

**Surface:**
```fundflow
let mgmt_rate = 1.5% per annum
```

**Semantic:** marks the inner expression as an annual rate. At runtime it's a pass-through; the *consumer* (e.g., `accrue` or `over … using`) is responsible for applying the right year-fraction.

### 3.11 `when <cond> then <stmt> [else <stmt>]` — conditional posting

**Surface:**
```fundflow
when fee > USD 0 then
  post fee to ledger account "Performance Fee Payable"
else
  publish skipped
```

**Semantic:** evaluates `<cond>` (must be Boolean). Runs `then` branch when true; `else` branch (if present) when false; nothing when null/unknown.

### 3.12 `sum of <expr> [by <dim>]` / `weighted average … weighted by …` — aggregations

**Surface:**
```fundflow
let total_mv = sum of position market value
let warr     = weighted average return weighted by capital
```

**Semantic:**
- `sum of <list>` returns the additive total of a `ListVal` of numeric values.
- `sum of <list> by <dim>` is parsed but not yet grouped at runtime.
- `weighted average <values> weighted by <weights>` returns `Σ(v_i × w_i) / Σ(w_i)` over equal-length lists.

## 4. Selectors

`applies to:` accepts a noun-phrase / `of`-chained reference. Common shapes:

```
applies to: fund "Beta PE Fund III"
applies to: share class "Class A" of fund "Alpha Master Fund LP"
applies to: all share classes of fund "Alpha Master Fund LP"
applies to: investors of fund "Beta PE Fund III"
```

The semantic phase compares selectors structurally — two rules with the same selector and overlapping `effective:` ranges raise [`FF3001`](diagnostics.md#3xxx--effectivity--scope).

## 5. Periods and dates

```
effective: from 2026-01-01                  // open-ended
effective: from 2026-01-01 to 2026-12-31    // bounded
effective: from inception                    // tied to fund's inception date
effective: Q1 2026                           // named/phrasal (resolved by catalog)
```

Date literals are strict ISO `YYYY-MM-DD`. Phrasal forms (`Q1 2026`, `month of March 2026`, `current crystallization period`, `valuation date`, `call date`) are deferred to the per-fund catalog ([`FF9001`](diagnostics.md#9xxx--deferred--informational)).

## 6. Type extensions

```fundflow
type extension PrivateEquityFund extends Fund {
  field commitment_period: Period
  field investment_period_end: BusinessDate
  field gp_commitment: Percentage
}
```

Registers an extension type for use in `applies to:` selectors and field references. Resolution against the symbol table happens in WP-11.

## 7. Determinism

Three guarantees the engine enforces:

1. **No wall-clock reads inside the interpreter.** "Now" is `EvaluationContext.asOf`. `today()` and `now()` are deliberately absent from the [`FunctionRegistry`](../stdlib/src/main/java/ai/getfundflow/dsl/stdlib/FunctionRegistry.java); calling either raises `FF2003`.
2. **Stable iteration order.** `outputs` is a `TreeMap`; `MapDataSource` sorts its backing store. Rerunning the same program with the same inputs yields a byte-identical SHA-256 audit hash (see `AuditTrail.contentHash()`).
3. **Explicit rounding.** Money values are normalized to currency fraction digits (HALF_EVEN) at construction. `MathContext.DECIMAL64` is the default for intermediate calculations; rounding decisions are recorded in the audit trail.
