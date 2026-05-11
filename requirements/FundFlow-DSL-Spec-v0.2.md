# FundFlow DSL — Technical Specification v0.2

**Project:** getFundFlow.ai Domain-Specific Language
**Target Users:** Fund Accounting & Fund Admin Operations (non-technical), assisted by AI Agents
**Implementation Stack:** ANTLR4 grammar + Java 21 runtime
**Status:** Draft for v1 implementation

**Companion documents (function libraries):**

- `FundFlow-Functions-Math.md` — arithmetic, rounding, powers, logs, trig, aggregates
- `FundFlow-Functions-Excel.md` — Excel-compat lookup, info, error model, compatibility matrix
- `FundFlow-Functions-Financial.md` — TVM, IRR/NPV, bonds, depreciation, day-count
- `FundFlow-Functions-Statistical.md` — descriptive stats, distributions, regression
- `FundFlow-Functions-DateTime.md` — date/time/period manipulation
- `FundFlow-Functions-Text.md` — string handling
- `FundFlow-Functions-Logical.md` — boolean and conditional
- `FundFlow-Functions-FundAccounting.md` — NAV, fees, allocations, waterfalls, equalization, carry

**Changelog from v0.1:**
- Added function-call grammar (§5.5) and dispatch rules (§6.5)
- Added `Range` and `Series` collection types (§4.7)
- Added WP-13 (Standard Function Library) and WP-14 (Excel Compatibility Layer)
- Function libraries split into companion documents to keep each spec focused
- AI agent contract (§14) extended to publish the function catalog

---

## 0. How to Use This Spec with Claude Code

This spec is structured as a sequence of self-contained work packages (WP-0 through WP-14). Each work package has explicit deliverables, file paths, acceptance criteria, and test expectations. Hand them to Claude Code one at a time. Do not skip ahead — later packages depend on the types and grammar produced earlier.

Recommended prompt template for each work package:

> "Implement WP-N from `FundFlow-DSL-Spec-v0.2.md`. For function implementations, follow the signatures in the relevant companion document (`FundFlow-Functions-*.md`). Follow the file structure, naming conventions, and acceptance criteria exactly. Produce the code, the unit tests, and update the README. Do not modify files outside the listed deliverables."

---

## 1. Project Goals & Non-Goals

### 1.1 Goals

- A declarative DSL that reads like a fund's offering documents, not like code
- First-class domain types: Money, Date, Period, Percentage, DayCount, Fund, ShareClass, NAV, Position, Transaction
- A comprehensive function library covering Math, Excel-compatible, Financial, Statistical, Date/Time, Text, Logical, and Fund-Accounting domains so ops users have parity with spreadsheet tools
- Deterministic, auditable execution with full bitemporal effective-dating
- Tooling that makes ops users productive: LSP, formatter, dry-run, rich errors
- Grammar-constrained AI agent authoring with a tight validate-and-retry loop
- Pure JVM runtime suitable for embedding in existing Java services

### 1.2 Non-Goals (v1)

- No general-purpose programming (no user-defined loops, recursion, or mutable state)
- No bytecode generation — tree-walking interpreter is sufficient for v1
- No multi-tenant runtime concerns — that lives at the platform layer
- No UI — only the LSP backend; the editor is provided by the platform
- No persistence layer — the engine is a pure function of inputs
- No Excel volatile functions (`NOW`, `RAND`, `RANDBETWEEN`, `OFFSET`, `INDIRECT`) — all evaluation is deterministic against `EvaluationContext.asOf`

---

## 2. Repository Layout

```
fundflow-dsl/
├── build.gradle.kts                       # Gradle Kotlin DSL build
├── settings.gradle.kts
├── gradle/libs.versions.toml              # Version catalog
├── README.md
├── docs/
│   ├── language-guide.md                  # User-facing
│   ├── grammar-reference.md               # Auto-generated from .g4
│   ├── function-reference.md              # Auto-generated from registry
│   ├── excel-compatibility-matrix.md      # Excel → FundFlow mapping
│   └── examples/                          # Canonical examples
├── grammar/
│   ├── FundFlowLexer.g4
│   └── FundFlowParser.g4
├── core/                                  # Module: types & utilities
│   └── src/main/java/ai/getfundflow/dsl/core/
│       ├── types/                         # Money, Period, BusinessDate, etc.
│       ├── calendar/                      # Business day calendars
│       └── util/
├── ast/                                   # Module: AST + visitor
├── parser/                                # Module: ANTLR-generated + AST builder
├── semantic/                              # Module: symbol resolution, type-check
├── runtime/                               # Module: tree-walking interpreter
├── stdlib/                                # Module: built-in operators + function library
│   └── src/main/java/ai/getfundflow/dsl/stdlib/
│       ├── operators/                     # Declarative verbs (accrue, allocate, …)
│       ├── functions/
│       │   ├── math/
│       │   ├── statistical/
│       │   ├── financial/
│       │   ├── datetime/
│       │   ├── text/
│       │   ├── logical/
│       │   ├── lookup/
│       │   ├── fundaccounting/
│       │   └── registry/                  # FunctionRegistry, dispatcher
│       └── compat/                        # Excel compatibility shims
├── diagnostics/                           # Module: errors, hints, formatting
├── lsp/                                   # Module: Language Server Protocol
├── cli/                                   # Module: command-line tools
└── examples/                              # End-to-end .ff programs
```

Each module is a separate Gradle subproject with explicit dependencies. Keep the dependency graph acyclic and minimal.

---

## 3. Build & Tooling

### 3.1 Required versions

- Java 21 LTS (records, sealed types, pattern matching)
- Gradle 8.7+
- ANTLR 4.13.x
- JUnit 5.10.x
- AssertJ 3.25+
- jqwik 1.8+ for property-based tests
- LSP4J 0.21+ for the language server
- Apache Commons Math 3.6.1 (statistical and numerical foundations) — wrapped, never exposed
- ch.obermuhlner:big-math 2.3.x (BigDecimal transcendental functions: ln, exp, pow, sin, cos)

### 3.2 Coding standards

- `BigDecimal` only for monetary and rate arithmetic. **No `double` or `float` anywhere in production code.** Enforce via ArchUnit.
- All public APIs return `Optional` rather than `null`.
- All domain values are immutable records or sealed interfaces.
- `MathContext.DECIMAL64` is the default for intermediate calculations; rounding is explicit at boundaries.
- Every public class has Javadoc explaining its role in fund accounting terms.
- Function implementations must declare their numerical precision contract in Javadoc (e.g. `@precision DECIMAL64 with HALF_EVEN rounding at output`).

---

## 4. Domain Types (WP-1)

These are implemented in Java first, before the grammar. The grammar's literal syntax mirrors these types.

### 4.1 Money

```java
public record Money(BigDecimal amount, Currency currency) {
    // Construction, arithmetic, conversion via FxRate
    // Mismatched-currency arithmetic throws CurrencyMismatchException
}
```

Literal syntax: `USD 1,250,000.00`, `EUR 50_000`, `JPY 1_000_000` (no decimals for JPY enforced).

### 4.2 BusinessDate

```java
public record BusinessDate(LocalDate date, BusinessCalendar calendar) {
    BusinessDate plusBusinessDays(int n);
    BusinessDate previousBusinessDay();
    boolean isBusinessDay();
}
```

Literal syntax: `2026-03-31`, `last business day of Q1 2026`, `T+2 from 2026-03-29 on NYSE`.

### 4.3 Period

```java
public sealed interface Period
    permits CalendarPeriod, NamedPeriod, RelativePeriod {

    LocalDate start();
    LocalDate end();
    long days();
    long businessDays(BusinessCalendar cal);
}
```

Literal syntax: `Q1 2026`, `month of March 2026`, `from 2026-01-01 to 2026-03-31`, `YTD`, `MTD`, `since inception`.

### 4.4 Percentage

```java
public record Percentage(BigDecimal value) {
    // 1.5% stored as 0.015
    Money applyTo(Money base);
    BigDecimal asRatio();
}
```

Literal syntax: `1.5%`, `25 bps`, `100 bps`.

### 4.5 DayCount

```java
public sealed interface DayCount
    permits Actual360, Actual365, Actual365L, Thirty360, ThirtyE360, ActualActual {

    BigDecimal yearFraction(LocalDate start, LocalDate end);
}
```

Literal syntax: `actual/360`, `actual/365`, `actual/365L`, `30/360`, `30E/360`, `actual/actual`.

### 4.6 Quantity

```java
public record Quantity(BigDecimal value, Unit unit) { }
public sealed interface Unit
    permits Shares, Units, Contracts, Custom { }
```

Quantity arithmetic across mismatched units fails at type-check time.

### 4.7 Range and Series

To support spreadsheet-like aggregation:

```java
public record Range<T>(List<T> values, Optional<RangeMetadata> meta) { }
public record Series<K, V>(LinkedHashMap<K, V> entries) { }  // ordered, e.g. time series
```

`Range` is the input to `SUM`, `AVERAGE`, `COUNT`, etc. `Series` underlies time-indexed aggregations (rolling NAV, MTD/YTD calculations).

### 4.8 Fund domain entities

Implement as records with a schema registry:

- `Fund` — id, name, base currency, inception date, calendar
- `ShareClass` — fund ref, class name, currency, fee schedule, hurdle
- `Series` — share class ref, series name, issue date, issue price
- `Investor` — id, name, jurisdiction, tax status
- `Position` — fund ref, instrument, quantity, cost basis
- `Transaction` — id, type, trade date, settle date, amount, parties
- `Cashflow` — date, direction, amount, classification
- `NAV` — fund ref, as-of date, gross assets, liabilities, units outstanding
- `LedgerAccount` — code, name, type, currency

These are open for extension via a schema-extension mechanism (WP-11).

### 4.9 Acceptance criteria for WP-1

- All types are immutable records or sealed interfaces
- Equality and hashing are value-based
- 100% unit test coverage on arithmetic and conversion
- Property-based tests: associativity of money addition within a currency, idempotence of period intersection
- A `core-types-cheatsheet.md` documents every type with examples

---

## 5. Grammar Design (WP-2, WP-3)

### 5.1 Lexer rules (highlights)

Split into `FundFlowLexer.g4` and `FundFlowParser.g4`. Use case-insensitive keyword fragments. Lex domain literals (Money, Date, Percentage, BPS, DayCount) as tokens to keep parser rules clean.

### 5.2 Parser rules

Top-level structure: program → moduleDecl? + importDecl* + topLevelDecl*. Top-level declarations are `ruleDecl`, `scheduleDecl`, `waterfallDecl`, `policyDecl`, `typeDecl`. Rules contain clauses: description, applies-to, effective, let-bindings, and statements.

### 5.3 Expression precedence (highest to lowest)

1. Postfix: `as of <date>`
2. Power: `^`, `**`
3. Unary: `-`, `+`, `NOT`
4. Multiplicative: `*`, `/`, `MOD`
5. Additive: `+`, `-`
6. Concatenation/range: `&`, `..`
7. Comparison: `=`, `!=`, `<`, `<=`, `>`, `>=`
8. Logical AND
9. Logical OR
10. Ternary: `cond ? a : b`

All implemented via ANTLR4 left-recursive expression rule.

### 5.4 Literals

Money, Date, Percentage, BPS, DayCount, Period, Decimal, Integer, String (quoted-ident), Boolean. Inline range literals: `[1, 2, 3]`. Range slices: `positions[fund="A"]`.

### 5.5 Function call syntax

Functions are first-class. Two surface forms supported:

```antlr
functionCall
    : qualifiedName '(' argList? ')'                     # standardCall
    | qualifiedName '(' namedArgList ')'                 # namedArgCall
    ;

argList      : expression (',' expression)* ;
namedArgList : namedArg (',' namedArg)* ;
namedArg     : IDENT '=' expression ;
```

Examples:

```fundflow
// Excel-style
SUM(positions.market_value)
ROUND(nav_per_unit, 4)
IRR(cashflows)
NPV(0.08, cashflows)

// Named args (preferred for ops users)
ROUND(value = nav_per_unit, digits = 4)
XIRR(cashflows = fund.cashflows, dates = fund.cashflow_dates, guess = 0.10)

// Namespaced (where ambiguity exists)
math.ln(x)
finance.year_fraction(start_date, end_date, day_count)
fund.nav_per_unit(share_class, valuation_date)
```

Function names are case-insensitive in source. The formatter canonicalizes Excel-compat names to UPPERCASE and namespaced/domain names to lowercase.

### 5.6 Acceptance criteria for WP-2

- Grammar files compile without ambiguity warnings (`antlr -Werror`)
- Test corpus of 100+ valid programs and 100+ deliberately invalid programs
- Each invalid program produces a single, locatable error (no parser cascades)

### 5.7 Acceptance criteria for WP-3 (AST builder)

- A visitor over the parse tree produces an AST defined as sealed interfaces in `ast/`
- AST is independent of ANTLR types — no `ParserRuleContext` references leak out
- Round-trip test: parse → build AST → pretty-print → parse again yields equivalent AST
- `FunctionCallNode` carries both positional and named argument forms

---

## 6. Semantic Analysis (WP-4)

### 6.1 Phases

1. **Symbol collection** — gather all rule, schedule, waterfall, and named-binding declarations into a `SymbolTable`
2. **Function resolution** — every function call resolves to a registered function signature; overloads dispatched by argument types
3. **Name resolution** — every identifier reference resolves to a symbol or produces an error
4. **Type inference and checking** — every expression is assigned a type; mismatches are errors
5. **Effectivity validation** — date ranges on rules don't overlap-conflict within the same scope

### 6.2 Type rules

| Operation | Allowed | Result |
|---|---|---|
| `Money + Money` (same currency) | yes | Money |
| `Money + Money` (different currency) | error | — |
| `Money * Percentage` | yes | Money |
| `Money * Money` | error | — |
| `Percentage + Percentage` | yes | Percentage |
| `BigDecimal * Money` | yes (warning if scalar > 1000) | Money |
| `Period.intersect(Period)` | yes | Optional<Period> |
| `Money as of Date` | yes | Money (revalued) |
| `Range<T> & Range<T>` | yes (concatenation) | Range<T> |
| `Range<T>[predicate]` | yes | Range<T> |

### 6.3 Diagnostics

Every error has: severity, code (e.g. `FF1042`), location (file/line/col/length), message, optional fix-it hint, and "did you mean" suggestion via Levenshtein distance ≤ 2 against in-scope symbols and the function registry.

### 6.4 Acceptance criteria for WP-4

- All type rules covered by unit tests
- Every diagnostic code documented in `docs/diagnostics.md`
- Performance: type-check 1000-line program in under 100ms

### 6.5 Function dispatch and overloading

Functions are dispatched by name and argument types. Overloads are resolved at type-check time; ambiguity is a hard error with a clear message listing all candidates.

```java
public record FunctionSignature(
    String namespace,        // "math", "finance", "fund", "" for Excel-compat
    String name,             // canonical name
    List<ParamSpec> params,
    Type returnType,
    Set<FunctionTrait> traits  // PURE, DETERMINISTIC, EXCEL_COMPAT, AGGREGATE, ITERATIVE
) { }

public record ParamSpec(
    String name,
    Type type,
    boolean optional,
    Optional<Object> defaultValue,
    boolean variadic
) { }
```

Resolution priority for unqualified names: (1) exact match in default namespace, (2) Excel-compat namespace, (3) any imported namespace. Ambiguity at any tier is an error, never silent fall-through.

### 6.6 Function purity and determinism

Every function has explicit traits. Non-deterministic Excel functions (`NOW`, `TODAY`, `RAND`, `RANDBETWEEN`) are not implemented — instead the language provides deterministic substitutes (`AS_OF_DATE()`, `VALUATION_DATE()`) bound to `EvaluationContext`.

---

## 7. Runtime / Interpreter (WP-5)

### 7.1 Architecture

A tree-walking interpreter over the typed AST. Pure functions of inputs. No hidden state.

```java
public interface Interpreter {
    EvaluationResult evaluate(Program program, EvaluationContext ctx);
}

public record EvaluationContext(
    BusinessDate asOf,
    DataSource data,
    BusinessCalendar defaultCalendar,
    AuditSink audit,
    FunctionRegistry functions
) { }

public record EvaluationResult(
    Map<String, Object> outputs,
    List<LedgerEntry> postings,
    AuditTrail trail
) { }
```

### 7.2 Determinism guarantees

- Same inputs + same program version + same as-of date ⇒ byte-identical outputs
- All rounding is explicit and recorded in the audit trail
- Wall-clock time is never read inside the interpreter; "now" is `EvaluationContext.asOf`
- Function invocations are recorded in the audit trail with input snapshots

### 7.3 Audit trail

Every computed value records: source rule, source location, function called (if any), inputs consumed, intermediate values, rounding decisions. Serializable as JSON for downstream review.

### 7.4 Acceptance criteria for WP-5

- Golden-file tests for the canonical examples (Section 9) match expected outputs exactly
- Property test: shuffling input order does not change outputs (where order shouldn't matter)
- Re-run determinism test: same program run 1000 times yields identical audit trail hashes

---

## 8. Standard Library Operators (WP-6)

These are first-class language constructs (declarative verbs), not user-callable functions. Each is implemented as an AST node with its own evaluator. Functions, in contrast, are call-syntax expressions (see §13 and the companion docs).

| Operator | Surface syntax | Semantics |
|---|---|---|
| Accrue | `accrue <rate> on <basis> using <day_count>` | `basis * rate * yearFraction(period, day_count)` |
| Allocate pro-rata | `allocate <amount> across <set> by <weight_field>` | Sum of weights → distribute proportionally; rounding adjustment to largest |
| Allocate equally | `allocate <amount> across <set> equally` | Equal split with rounding policy |
| Waterfall | `distribute <amount> through waterfall <name>` | Tier-by-tier distribution per named waterfall definition |
| As-of | `<expr> as of <date>` | Evaluate expr against historical state at date |
| Aggregation verb | `sum of <field> by <dimension>` | Grouped sum; also `weighted average ... weighted by ...` |
| Conditional | `when <cond> then <stmt> else <stmt>` | Declarative branching |
| Posting | `post <amount> to <account> with narrative <text>` | Generates LedgerEntry in result |

### 8.1 Acceptance criteria for WP-6

- Each operator has: spec section in `docs/language-guide.md`, ≥3 unit tests, 1 golden-file test
- Allocation operators: invariant test that outputs always sum to input within rounding tolerance

---

## 9. Canonical Examples (WP-7)

These are both documentation and AI-agent training data. Each must parse, type-check, evaluate, and produce documented expected outputs.

### 9.1 Management fee accrual

```fundflow
rule "Management Fee Daily Accrual" {
  description: "1.5% per annum on opening NAV, accrued daily, payable quarterly"
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

### 9.2 Performance fee with hurdle and high-water mark

```fundflow
rule "Performance Fee" {
  description: "20% over 8% hurdle with high-water mark, crystallized annually"
  applies to: share class "Class A" of fund "Alpha Master Fund LP"
  effective: from 2026-01-01

  let hurdle_rate  = 8% per annum
  let perf_rate    = 20%
  let period       = current crystallization period

  let gross_return  = nav at end of period - nav at start of period
  let hurdle_amount = nav at start of period * hurdle_rate over period using actual/365
  let excess        = MAX(0, gross_return - hurdle_amount)
  let above_hwm     = MAX(0, nav at end of period - high water mark)

  let fee = perf_rate * MIN(excess, above_hwm)

  when fee > USD 0 then
    post fee to ledger account "Performance Fee Payable"
      with narrative "Annual perf fee crystallization"
}
```

### 9.3 Capital call allocation

```fundflow
rule "Capital Call Allocation" {
  description: "Allocate capital call across investors pro-rata to unfunded commitment"
  applies to: fund "Beta PE Fund III"

  let call_amount = USD 50,000,000
  let basis       = unfunded commitment of investor as of call date

  allocate call_amount across investors of fund by basis
  post each allocation to ledger account "Capital Called Receivable"
}
```

### 9.4 NAV calculation

```fundflow
rule "Daily NAV Strike" {
  description: "Standard NAV per unit calculation"
  applies to: all share classes of fund "Alpha Master Fund LP"
  effective: from inception

  let gross_assets   = SUM(positions.market_value as of valuation date)
  let liabilities    = SUM(accrued_expenses + payables as of valuation date)
  let net_assets     = gross_assets - liabilities
  let units          = units outstanding as of valuation date

  let nav_per_unit = ROUND(net_assets / units, 4)

  publish nav as of valuation date
}
```

### 9.5 IRR-driven carry calculation

```fundflow
rule "GP Carry — IRR-Based" {
  description: "GP earns 20% carry once fund IRR exceeds 8% preferred return"
  applies to: fund "Beta PE Fund III"

  let cf       = fund.cashflows ordered by date
  let dates    = fund.cashflow_dates
  let fund_irr = XIRR(cf, dates)
  let pref     = 8%

  let carry = IF(fund_irr > pref,
                 0.20 * (fund_irr - pref) * fund.invested_capital,
                 USD 0)

  post carry to ledger account "GP Carried Interest"
}
```

### 9.6 Equalization (series accounting)

To be authored as part of WP-7 — full equalization with side-pocket carve-outs.

### 9.7 Waterfall distribution

To be authored as part of WP-7 — European-style waterfall with return of capital, preferred return, GP catch-up, and 80/20 split.

### 9.8 Acceptance criteria for WP-7

- Each example file lives in `examples/` with a paired `.expected.json` golden output
- README documents the financial logic of each in plain English
- All examples parse, type-check, and evaluate successfully

---

## 10. Diagnostics & Formatter (WP-8)

### 10.1 Error message format

```
error[FF1042]: currency mismatch in addition
  --> management_fee.ff:7:23
   |
 7 |   let total = base_fee + EUR 1000
   |               -------- ^^^^^^^^^^ this is EUR
   |               |
   |               this is USD
   |
   = help: insert an FX conversion: `EUR 1000 in USD as of valuation date`
```

### 10.2 Formatter rules

- Two-space indentation inside `rule { }` blocks
- One blank line between top-level declarations
- Keywords lowercase, identifiers as written, currency codes uppercase
- Excel-compatible function names UPPERCASE (`SUM`, `XIRR`, `VLOOKUP`)
- Namespaced function names lowercase with dot separators (`math.ln`, `fund.nav_per_unit`)
- Money literals: thousands separators with underscores or commas (canonicalize to commas)
- Trailing whitespace stripped, files end with newline

### 10.3 Acceptance criteria for WP-8

- Formatter is idempotent: `format(format(x)) == format(x)` for all valid programs
- All error messages tested via snapshot tests
- Levenshtein-based "did you mean" suggestions for unknown identifiers and unknown function names

---

## 11. LSP Server (WP-9)

### 11.1 Capabilities

- `textDocument/didOpen|didChange` with incremental parsing
- `textDocument/publishDiagnostics` from semantic phase
- `textDocument/completion` — context-aware: keywords, in-scope symbols, schema fields, function names with signature snippets and Excel-compatibility annotations
- `textDocument/hover` — type info, doc strings, evaluated examples, function signature with parameter docs and worked example
- `textDocument/definition` — go-to-rule, go-to-schedule
- `textDocument/signatureHelp` — for function calls, show active parameter
- `textDocument/formatting` — invokes the formatter
- `textDocument/codeAction` — apply fix-it hints from diagnostics, including "convert Excel formula" actions

### 11.2 Acceptance criteria for WP-9

- Manual smoke test in VS Code with the official LSP4J reference client
- Round-trip latency under 50ms for files under 500 lines
- Completion accuracy: expected symbol or function appears in top-3 in 95% of test cases
- Hover on any function shows full signature, return type, and one example

---

## 12. CLI Tools (WP-10)

```
fundflow parse <file.ff>            # Parse and print AST
fundflow check <file.ff>            # Run full semantic analysis
fundflow run <file.ff> --as-of D    # Execute against test fixture
fundflow format <file.ff>           # Format in place
fundflow lsp                        # Start the LSP server on stdio
fundflow functions                  # List all registered functions
fundflow functions <name>           # Show signature, docs, examples
fundflow excel-import <file.xlsx>   # Convert Excel formulas to FundFlow (best-effort)
```

All commands exit with non-zero status on any error. Machine-readable JSON output via `--json` flag.

---

## 13. Standard Function Library (WP-13, WP-14)

The function library is split across companion documents to keep each spec focused. Every function is registered in a single `FunctionRegistry` keyed by `(namespace, name, arity)`.

### 13.1 Namespaces

| Namespace | Document | Purpose |
|---|---|---|
| (empty) | `FundFlow-Functions-Excel.md` | Excel-compatible names usable without prefix |
| `math.` | `FundFlow-Functions-Math.md` | Mathematical functions |
| `stat.` | `FundFlow-Functions-Statistical.md` | Statistical functions |
| `finance.` | `FundFlow-Functions-Financial.md` | Time-value-of-money and financial functions |
| `date.` | `FundFlow-Functions-DateTime.md` | Date and period manipulation |
| `text.` | `FundFlow-Functions-Text.md` | String handling |
| `logic.` | `FundFlow-Functions-Logical.md` | Boolean and conditional |
| `fund.` | `FundFlow-Functions-FundAccounting.md` | Fund-accounting domain functions |

Excel-compatible function names appear in BOTH the empty namespace and their domain namespace. For example, `SUM` is callable as `SUM(...)` (Excel-style) or `math.sum(...)` (namespaced). This dual registration is the basis for the Excel-import tool.

### 13.2 Function traits

```java
public enum FunctionTrait {
    PURE,                // No side effects
    DETERMINISTIC,       // Same inputs → same outputs
    EXCEL_COMPAT,        // Matches Excel semantics within tolerance
    AGGREGATE,           // Operates on Range or Series
    ITERATIVE,           // Uses iterative numerical method (IRR, RATE, etc.)
    REQUIRES_CONTEXT,    // Needs EvaluationContext (e.g. fx_convert)
    DOMAIN_FUND          // Fund-accounting domain function
}
```

All v1 functions are PURE and DETERMINISTIC. Functions tagged ITERATIVE expose convergence parameters (max iterations, tolerance) with documented defaults.

### 13.3 Function metadata

Each function entry in the registry carries:

- Canonical name and namespace
- Full signature (parameter names, types, optionality, defaults, variadic)
- Return type
- Trait set
- One-paragraph description
- ≥ 1 worked example with expected output
- Excel parity reference (cell formula equivalent, if EXCEL_COMPAT)
- Edge-case behavior notes (division by zero, empty range, negative inputs, etc.)

This metadata drives: LSP hover/completion, the function reference doc generator, the AI agent function catalog (§14), and the `fundflow functions <name>` CLI.

### 13.4 Acceptance criteria for WP-13 (Standard Function Library)

- Math, Statistical, DateTime, Text, Logical, Lookup namespaces complete per their companion docs
- ≥ 3 unit tests per function (happy path, edge case, error case)
- Property-based tests for invariants (commutativity of SUM, monotonicity of MAX, etc.)
- Generated `function-reference.md` matches registry contents
- All functions documented in their respective companion docs are implemented

### 13.5 Acceptance criteria for WP-14 (Excel Compatibility Layer)

- 100% of functions tagged EXCEL_COMPAT have parity test fixtures (`.csv` of inputs → expected output, computed in Excel, committed to repo)
- Parity test asserts equality within documented tolerance (typically `1e-10` for non-monetary, exact for monetary with explicit rounding)
- `excel-compatibility-matrix.md` auto-generated showing FundFlow coverage of Excel's full function list with status: implemented / replaced / not-implemented (with rationale)
- `fundflow excel-import` tool converts simple formulas with documented accuracy on a test corpus of 100+ formulas

---

## 14. AI Agent Integration (WP-12)

### 14.1 Agent contract

The platform's AI agent generates `.ff` programs. Provide:

1. **Grammar artifact** — published copy of `FundFlowLexer.g4` and `FundFlowParser.g4` for grammar-constrained decoding
2. **Type catalog** — JSON schema describing all built-in types and currently registered extensions
3. **Symbol catalog** — JSON listing all in-scope rules, schedules, waterfalls, and ledger accounts for the target fund
4. **Function catalog** — JSON listing every registered function with signature, traits, one-sentence description, and ≥1 example. Generated from the registry, never hand-maintained
5. **Example corpus** — the canonical examples from §9 with paired natural-language descriptions
6. **Validate-and-retry endpoint** — POST a candidate program, receive parse/type/eval diagnostics in structured JSON; the agent loop retries with diagnostics in the next prompt until clean

### 14.2 Determinism for the agent

The agent is forbidden from inventing identifiers or function names. Every reference must resolve against the symbol or function catalog. The validator rejects unknown names rather than coercing them.

### 14.3 Acceptance criteria for WP-12

- `POST /v1/dsl/validate` returns `{parse, types, eval}` arrays of diagnostics in under 500ms
- `GET /v1/dsl/catalog?fund_id=X` returns the merged symbol + type + function catalog for a fund
- An end-to-end test: natural-language prompt → agent generates → validator approves on attempt ≤ 3 for each canonical example

---

## 15. Schema Extension Mechanism (WP-11)

Customers will need to extend the domain model (custom transaction types, custom fund attributes). Provide a schema definition file:

```fundflow
type extension PrivateEquityFund extends Fund {
  field commitment_period: Period
  field investment_period_end: BusinessDate
  field gp_commitment: Percentage
}
```

The schema is loaded before grammar parsing and registered in the symbol table.

### 15.1 Acceptance criteria for WP-11

- Type extensions resolved during semantic phase
- Existing rules referencing extended fields type-check successfully
- Extensions are versioned alongside the rule modules that use them

---

## 16. Testing Strategy (cross-cutting)

| Layer | Tool | Coverage target |
|---|---|---|
| Domain types | JUnit + jqwik | 100% line, 95% mutation (Pitest) |
| Grammar | ANTLR test rig + JUnit | 100% rule coverage |
| AST builder | JUnit (round-trip) | 100% node types |
| Semantic | JUnit (snapshot) | 100% diagnostic codes |
| Runtime | Golden-file + property | All canonical examples |
| Function library | JUnit + Excel parity tests | 100% functions, ≥3 cases each |
| LSP | Manual + LSP4J test client | All capabilities |

Every module has a `src/test/` tree mirroring `src/main/`. CI runs the full suite on every PR.

### 16.1 Excel parity testing

For every function tagged `EXCEL_COMPAT`, maintain a parity test fixture: a CSV of `inputs → expected output` where the expected output was computed in Excel/Calc and committed to the repo. The test feeds the same inputs through FundFlow and asserts equality within documented tolerance.

---

## 17. Roadmap & Sequencing

| Sprint | Work Packages | Output |
|---|---|---|
| 1 | WP-0 setup, WP-1 domain types | Compilable modules, published types |
| 2 | WP-2 grammar, WP-3 AST | Parse → AST round-trip working |
| 3 | WP-4 semantic | Type-checker green on all examples |
| 4 | WP-5 runtime, WP-6 stdlib operators | Mgmt fee + NAV examples evaluating |
| 5 | WP-13 part A: math, stat, datetime, text, logical, lookup | Function registry online, ~250 functions live |
| 6 | WP-13 part B + WP-14: financial functions + Excel parity | IRR/NPV/XIRR live, parity tests passing |
| 7 | WP-13 part C: fund-accounting functions | NAV/fee/allocation/waterfall functions live |
| 8 | WP-7 examples (all + IRR-carry) | Full canonical corpus + golden files |
| 9 | WP-8 diagnostics + formatter | Production-quality errors |
| 10 | WP-9 LSP, WP-10 CLI | Editor experience usable |
| 11 | WP-11 extensions, WP-12 agent | Customer customization + agent loop |

Each sprint ends with a runnable demo and an updated `CHANGELOG.md`.

---

## 18. Open Questions for the Project Owner

These need decisions before WP-7 (canonical examples) and WP-11 (extensions):

1. Which day-count conventions beyond the listed are required for v1?
2. Which business calendars must ship in v1? (NYSE, LSE, TARGET2, JPX, custom?)
3. What's the rounding policy for per-investor allocations — bankers' rounding, half-up, or "largest residual"?
4. Are FX rates a static lookup table or pulled from a live source via a `DataSource` interface?
5. What's the canonical bitemporal model — does the engine need to support corrections (known date in the past)?
6. What auth model does the LSP need (stdio only, or remote with mTLS)?
7. **For Excel compatibility: do we surface Excel error values (`#DIV/0!`, `#N/A`) as a typed `Error` value or as hard exceptions caught only by `IFERROR` / `IFNA`?** Recommended: hard exceptions.
8. **Which financial functions are mandatory for v1 vs deferred to v2?** Recommended v1 minimum set is in `FundFlow-Functions-Financial.md` §1.
9. **What are the iterative-method defaults for IRR/XIRR/RATE?** Recommended: max 100 iterations, tolerance `1e-10`, configurable per call.

Resolve these before sprint 5.

---

## 19. References

- Terence Parr, *The Definitive ANTLR 4 Reference*
- Martin Fowler, *Domain-Specific Languages*
- Microsoft Office Excel Function Reference (current)
- ISO/IEC 29500 (OOXML) — Excel formula semantics
- ISDA documentation — day-count conventions
- IFRS 9 and US GAAP — accrual and recognition rules
- AICPA *Audit and Accounting Guide: Investment Companies* — fund accounting standards

---

*End of Core Specification v0.2. Continue with the function library companion documents.*
