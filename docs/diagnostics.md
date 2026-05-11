# FundFlow Diagnostics Catalog

Every diagnostic emitted by the semantic phase has a stable `FFxxxx` code, a default severity, and a summary. Codes are partitioned by category:

- **1xxx** — Type errors (arithmetic, currency, operands)
- **2xxx** — Symbol / name resolution
- **3xxx** — Effectivity / scope
- **4xxx** — Determinism / safety
- **9xxx** — Deferred / informational

Severity column: `E` = error (blocks downstream phases), `W` = warning (advisory), `I` = informational.

## 1xxx — Type errors

| Code | Sev | Summary | Triggers |
|---|---|---|---|
| `FF1001` | E | Type mismatch | An expression's inferred type doesn't match the contextually required type (e.g., `accrue` rate isn't a Percentage). |
| `FF1042` | E | Currency mismatch | `Money + Money`, `Money - Money`, comparison, or equality where the two currencies differ. |
| `FF1043` | E | `Money * Money` is not allowed | Multiplying two Money values produces a meaningless dimension. Use `Money * Percentage` or `Money / Money` (returns a scalar) instead. |
| `FF1044` | E | Unit mismatch | `Quantity + Quantity` or similar with incompatible units. |
| `FF1050` | E | Invalid operand types for operator | Catch-all for binary/unary operators applied to types that have no rule (e.g., `Money + Boolean`). |
| `FF1060` | W | Large scalar on Money | A `BigDecimal * Money` where the scalar's absolute value exceeds 1000 — likely a forgotten `%`. |
| `FF1100` | E | Extension extends unknown base type | A `type extension X extends Y { … }` where `Y` is not one of the built-in base types (Fund, ShareClass, Series, Investor, Position, Transaction, Cashflow, NAV, LedgerAccount). |
| `FF1101` | E | Unknown extension field | A phrasal `<field> of <base_type>` reference where the named base type has at least one extension in scope, but none of those extensions declares the requested field. |
| `FF1102` | E | Unknown field type | A `field x: T` declaration where `T` is not a recognized DSL type (Money / Percentage / Number / Boolean / BusinessDate / Period / DayCount / Quantity / String). |

## 2xxx — Symbol / name errors

| Code | Sev | Summary | Triggers |
|---|---|---|---|
| `FF2001` | E | Duplicate declaration | Two top-level decls with the same name, or two `let` bindings sharing a name within one rule. |
| `FF2002` | E | Unresolved binding | A bare-identifier reference (e.g., `let_bound_name`) that doesn't match any in-scope `let` binding for the current rule. |
| `FF2003` | E | Unknown function | A function call to a name not in the `FunctionRegistry`. |
| `FF2004` | E | Function arity mismatch | Wrong number of arguments for the named function (compared against `FunctionRegistry` `minArity`/`maxArity`). |

## 3xxx — Effectivity / scope

| Code | Sev | Summary | Triggers |
|---|---|---|---|
| `FF3001` | E | Overlapping effective periods | Two rules with the **same** `applies to:` selector and overlapping `effective:` date ranges. Only checks rules whose `effective` clauses use literal dates (`from D` or `from D to D`). |
| `FF3002` | E | Inverted period | `from D1 to D2` where `D2 < D1`. |

## 4xxx — Determinism / safety

| Code | Sev | Summary | Triggers |
|---|---|---|---|
| `FF4001` | E | Forbidden function | Use of a function the engine refuses for determinism reasons. (`today()` / `now()` are reserved cases — they are intentionally **not** in the `FunctionRegistry`, so they trigger `FF2003`. `FF4001` is reserved for future explicitly-blocked names.) |

## 9xxx — Deferred / informational

| Code | Sev | Summary | Triggers |
|---|---|---|---|
| `FF9001` | I | Deferred phrasal reference | A multi-word or `of`-chained reference such as `opening nav of share class` or `unfunded commitment of investor`. The semantic phase cannot resolve these without a per-fund domain catalog (delivered alongside WP-12). They are not errors; they are markers for the runtime / agent layer. |

## Diagnostic format

```
error[FF1042]: currency mismatch in addition
  --> management_fee.ff:7:23
  = help: insert an FX conversion: `EUR 1000 in USD as of valuation date`
```

Diagnostics are emitted by `ai.getfundflow.dsl.semantic.SemanticAnalyzer` in three phases — symbol collection, type checking, effectivity validation — and accumulated in a single `Diagnostics` bag. `Diagnostics.errors()`, `Diagnostics.warnings()`, and `Diagnostics.hasErrors()` filter by severity. Rendering uses `Diagnostic.render()`.

## Adding a new code

1. Add an entry to `DiagnosticCode` (semantic module) with a stable `FFxxxx` code, default severity, and one-line summary.
2. Emit it from the relevant phase via `Diagnostic.of(code, location, message)` or one of the `error` / `warning` factories.
3. Add a row to this document under the right category.
4. If the new code is exposed to the WP-12 AI agent loop (i.e., the agent should learn to fix the underlying problem), add it to the agent prompt template too.
