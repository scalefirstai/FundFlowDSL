# FundFlow Functions — Logical

**Companion to:** `FundFlow-DSL-Spec-v0.2.md`
**Namespace:** `logic.` (with Excel-compatible names also in the empty namespace)
**Implementation module:** `stdlib/.../functions/logical/`

Boolean and conditional functions. Short — but central to writing readable rules.

## 1. Conditional

| Function | Signature | Excel | Notes |
|---|---|---|---|
| `IF` | `IF(cond: B, then: T, else: T?) → T` | ★ | `else` defaults to FALSE in Excel; FundFlow requires explicit else for non-boolean returns |
| `IFS` | `IFS(...cond_value_pairs: (B, Any)) → Any` | ★ | First true wins |
| `SWITCH` | `SWITCH(expr, ...value_result_pairs, default?) → Any` | ★ | |
| `IFERROR` | `IFERROR(expr, fallback) → Any` | ★ | Catches all evaluation exceptions |
| `IFNA` | `IFNA(expr, fallback) → Any` | ★ | Catches only `NotAvailableException` |
| `IFBLANK` | `IFBLANK(expr, fallback) → Any` | (FundFlow) | Returns fallback if expr is blank/null |
| `COALESCE` | `COALESCE(...values) → Any` | (FundFlow) | First non-blank value |

### 1.1 Type rule for `IF`

The `then` and `else` branches must have a common supertype. Mismatched types are a compile error:

```fundflow
let x = IF(cond, USD 100, 200)        // error: Money vs Number
let x = IF(cond, USD 100, USD 0)      // ok: both Money
let x = IF(cond, USD 100, USD 0.eur)  // error: currency mismatch
```

This is stricter than Excel (which silently coerces) but catches real bugs.

## 2. Boolean operators

| Function | Signature | Excel | Notes |
|---|---|---|---|
| `AND` | `AND(...x: B \| R<B>) → B` | ★ | Short-circuit |
| `OR` | `OR(...x: B \| R<B>) → B` | ★ | Short-circuit |
| `NOT` | `NOT(x: B) → B` | ★ | |
| `XOR` | `XOR(...x: B) → B` | ★ | True if odd number of true args |
| `TRUE` | `TRUE() → B` | ★ | Constant |
| `FALSE` | `FALSE() → B` | ★ | Constant |
| `logic.implies` | `logic.implies(a: B, b: B) → B` | | Material implication |
| `logic.iff` | `logic.iff(a: B, b: B) → B` | | Biconditional |

## 3. Empty range behavior

- `AND(empty) = TRUE` (vacuous truth — Excel agrees)
- `OR(empty) = FALSE`
- `XOR(empty) = FALSE`

## 4. Worked examples

```fundflow
// Performance fee crystallization gate
let crystallize = AND(
    period_end_date = date.year_end(period_end_date),
    nav_per_unit > high_water_mark,
    fund_age_years >= 1
)

// Tiered fee rate
let mgmt_rate = SWITCH(
    aum_tier,
    "small",  1.50%,
    "medium", 1.25%,
    "large",  1.00%,
    /* default */ 1.50%
)

// Default to zero if no IRR computable
let irr = IFERROR(XIRR(cf, dates), 0%)

// First non-blank investor preferred name
let display_name = COALESCE(investor.preferred_name,
                            investor.legal_name,
                            "Unknown Investor")
```

## 5. Acceptance criteria

- All Excel-compat functions implemented with parity tests
- `IFERROR` correctly catches every documented exception subclass from §4 of the Excel doc
- `IFNA` catches only `NotAvailableException` and re-raises others
- Short-circuit semantics tested for `AND` and `OR` (subsequent args not evaluated)
- Type-checker rejects `IF` with incompatible branch types and provides actionable error message
- `SWITCH` and `IFS` exhaustiveness: if no branch matches and no default given, `IFS` raises `FF2060 IFS no matching condition`; `SWITCH` raises `FF2061 SWITCH no matching value` — both with location info

## 6. Implementation notes

- **Short-circuit for `AND`/`OR`:** evaluate args left-to-right and stop on first determining result. This requires lazy argument evaluation in the interpreter — implement as an AST-level special form, not a regular function call. Document this in `runtime/`.
- **Variadic with mixed scalars and ranges:** flatten before evaluation. `AND(true, [true, false])` → `AND(true, true, false)` → false.
- **`IFS` order:** evaluation strictly left-to-right; first true wins. Document this because some users expect "best match" semantics.
- **`IFERROR` performance:** the catch wraps a try/catch in Java. Don't use it as a control-flow workhorse — it's for genuine error recovery. The LSP can warn on patterns that look like control flow abuse.
- **`COALESCE`:** "blank" means `null`/`Optional.empty`/empty string. Document the exact rule. Recommended: `Optional.empty` and explicit blank values count as blank; zero, FALSE, and empty ranges do NOT count as blank.

---

*End of Logical Functions Reference.*
