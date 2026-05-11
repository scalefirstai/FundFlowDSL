# FundFlow DSL — Technical Specification v0.1

**Project:** getFundFlow.ai Domain-Specific Language
**Target Users:** Fund Accounting & Fund Admin Operations (non-technical), assisted by AI Agents
**Implementation Stack:** ANTLR4 grammar + Java 21 runtime
**Status:** Draft for v1 implementation

---

## 0. How to Use This Spec with Claude Code

This spec is structured as a sequence of self-contained work packages (WP-0 through WP-12). Each work package has explicit deliverables, file paths, acceptance criteria, and test expectations. Hand them to Claude Code one at a time. Do not skip ahead — later packages depend on the types and grammar produced earlier.

Recommended prompt template for each work package:

> "Implement WP-N from `FundFlow-DSL-Spec-v0.1.md`. Follow the file structure, naming conventions, and acceptance criteria exactly. Produce the code, the unit tests, and update the README. Do not modify files outside the listed deliverables."

---

## 1. Project Goals & Non-Goals

### 1.1 Goals

- A declarative DSL that reads like a fund's offering documents, not like code
- First-class domain types: Money, Date, Period, Percentage, DayCount, Fund, ShareClass, NAV, Position, Transaction
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
│   └── src/main/java/ai/getfundflow/dsl/ast/
├── parser/                                # Module: ANTLR-generated + AST builder
│   └── src/main/java/ai/getfundflow/dsl/parser/
├── semantic/                              # Module: symbol resolution, type-check
│   └── src/main/java/ai/getfundflow/dsl/semantic/
├── runtime/                               # Module: tree-walking interpreter
│   └── src/main/java/ai/getfundflow/dsl/runtime/
├── stdlib/                                # Module: built-in operators
│   └── src/main/java/ai/getfundflow/dsl/stdlib/
├── diagnostics/                           # Module: errors, hints, formatting
│   └── src/main/java/ai/getfundflow/dsl/diagnostics/
├── lsp/                                   # Module: Language Server Protocol
│   └── src/main/java/ai/getfundflow/dsl/lsp/
├── cli/                                   # Module: command-line tools
│   └── src/main/java/ai/getfundflow/dsl/cli/
└── examples/                              # End-to-end .ff programs
```

Each module is a separate Gradle subproject with explicit dependencies. Keep the dependency graph acyclic and minimal.

---

## 3. Build & Tooling

### 3.1 Required versions

- Java 21 LTS (use records, sealed types, pattern matching)
- Gradle 8.7+
- ANTLR 4.13.x
- JUnit 5.10.x
- AssertJ 3.25+
- jqwik 1.8+ for property-based tests
- LSP4J 0.21+ for the language server

### 3.2 Coding standards

- `BigDecimal` only for monetary and rate arithmetic. **No `double` or `float` anywhere in production code.** Enforce via ArchUnit or Error Prone.
- All public APIs return `Optional` rather than `null`.
- All domain values are immutable records or sealed interfaces.
- `MathContext.DECIMAL64` is the default for intermediate calculations; rounding is explicit at boundaries.
- Every public class has Javadoc explaining its role in fund accounting terms.

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
    // Distinct from raw BigDecimal so type system can enforce
    Money applyTo(Money base);
    BigDecimal asRatio();
}
```

Literal syntax: `1.5%`, `25 bps`, `100 bps`.

### 4.5 DayCount

```java
public sealed interface DayCount
    permits Actual360, Actual365, Thirty360, ActualActual {

    BigDecimal yearFraction(LocalDate start, LocalDate end);
}
```

Literal syntax: `actual/360`, `actual/365`, `30/360`, `actual/actual`.

### 4.6 Quantity

```java
public record Quantity(BigDecimal value, Unit unit) { }
public sealed interface Unit
    permits Shares, Units, Contracts, Custom { }
```

Quantity arithmetic across mismatched units fails at type-check time.

### 4.7 Fund domain entities

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

### 4.8 Acceptance criteria for WP-1

- All types are immutable records or sealed interfaces
- Equality and hashing are value-based
- 100% unit test coverage on arithmetic and conversion
- Property-based tests: associativity of money addition within a currency, idempotence of period intersection, etc.
- A `core-types-cheatsheet.md` documents every type with examples

---

## 5. Grammar Design (WP-2, WP-3)

### 5.1 Lexer rules (highlights)

```antlr
lexer grammar FundFlowLexer;

// Keywords (case-insensitive via fragment trick)
RULE        : R U L E ;
LET         : L E T ;
WHEN        : W H E N ;
APPLIES     : A P P L I E S ;
EFFECTIVE   : E F F E C T I V E ;
ACCRUE      : A C C R U E ;
ALLOCATE    : A L L O C A T E ;
DISTRIBUTE  : D I S T R I B U T E ;
AS_OF       : A S WS O F ;
BY          : B Y ;
ACROSS      : A C R O S S ;
USING       : U S I N G ;
ON          : O N ;
PER         : P E R ;
ANNUM       : A N N U M ;
// ... full keyword list in grammar file

// Domain literals
MONEY_LITERAL   : CURRENCY_CODE WS+ DECIMAL ;
DATE_LITERAL    : DIGIT DIGIT DIGIT DIGIT '-' DIGIT DIGIT '-' DIGIT DIGIT ;
PCT_LITERAL     : DECIMAL '%' ;
BPS_LITERAL     : DECIMAL WS+ 'bps' ;
DAYCOUNT_LIT    : ('actual' | '30') '/' ('360' | '365' | 'actual') ;

// Identifiers
IDENT           : [a-zA-Z_][a-zA-Z0-9_]* ;
QUOTED_IDENT    : '"' ~["]* '"' ;

// Comments and whitespace skipped
LINE_COMMENT    : '//' ~[\r\n]* -> skip ;
BLOCK_COMMENT   : '/*' .*? '*/' -> skip ;
WS              : [ \t\r\n]+ -> skip ;

// Case-insensitive fragments
fragment A : [aA] ; fragment B : [bB] ; // ... etc
```

### 5.2 Parser rules (highlights)

```antlr
parser grammar FundFlowParser;
options { tokenFrom=FundFlowLexer; }

program
    : moduleDecl? importDecl* topLevelDecl* EOF
    ;

topLevelDecl
    : ruleDecl
    | scheduleDecl
    | waterfallDecl
    | policyDecl
    | typeDecl
    ;

ruleDecl
    : RULE name=QUOTED_IDENT '{'
        ruleClause*
      '}'
    ;

ruleClause
    : 'description' ':' QUOTED_IDENT
    | 'applies' 'to' ':' targetSelector
    | EFFECTIVE ':' periodExpr
    | LET IDENT '=' expression
    | statement
    ;

statement
    : accrueStmt
    | allocateStmt
    | distributeStmt
    | postStmt
    | ifStmt
    ;

// Expression precedence via left-recursive rules
expression
    : expression AS_OF dateExpr                # asOfExpr
    | expression op=('*'|'/') expression       # mulDivExpr
    | expression op=('+'|'-') expression       # addSubExpr
    | expression op=COMPARE expression         # compareExpr
    | expression AND expression                # andExpr
    | expression OR expression                 # orExpr
    | functionCall                             # funcCallExpr
    | literal                                  # literalExpr
    | qualifiedName                            # nameExpr
    | '(' expression ')'                       # parenExpr
    ;

literal
    : MONEY_LITERAL | DATE_LITERAL | PCT_LITERAL
    | BPS_LITERAL | DAYCOUNT_LIT | periodLiteral
    | DECIMAL | INTEGER | QUOTED_IDENT
    ;

// ... full grammar in grammar/FundFlowParser.g4
```

### 5.3 Acceptance criteria for WP-2

- Grammar files compile without ambiguity warnings (`antlr -Werror`)
- Test corpus of 50+ valid programs and 50+ deliberately invalid programs
- Each invalid program produces a single, locatable error (no parser cascades)

### 5.4 Acceptance criteria for WP-3 (AST builder)

- A visitor over the parse tree produces an AST defined as sealed interfaces in `ast/`
- AST is independent of ANTLR types — no `ParserRuleContext` references leak out
- Round-trip test: parse → build AST → pretty-print → parse again yields equivalent AST

---

## 6. Semantic Analysis (WP-4)

### 6.1 Phases

1. **Symbol collection** — gather all rule, schedule, waterfall, and named-binding declarations into a `SymbolTable`.
2. **Name resolution** — every identifier reference resolves to a symbol or produces an error.
3. **Type inference and checking** — every expression is assigned a type; mismatches are errors.
4. **Effectivity validation** — date ranges on rules don't overlap-conflict within the same scope.

### 6.2 Type rules

| Operation | Allowed | Result |
|---|---|---|
| `Money + Money` (same currency) | yes | Money |
| `Money + Money` (different currency) | error | — |
| `Money * Percentage` | yes | Money |
| `Money * Money` | error | — |
| `Percentage + Percentage` | yes | Percentage |
| `BigDecimal * Money` | yes (with warning if scalar > 1000, may indicate forgotten %) | Money |
| `Period.intersect(Period)` | yes | Optional<Period> |
| `Money as of Date` | yes | Money (revalued) |

### 6.3 Diagnostics

Every error has: severity, code (e.g. `FF1042`), location (file/line/col/length), message, optional fix-it hint, and "did you mean" suggestion via Levenshtein distance ≤ 2 against in-scope symbols.

### 6.4 Acceptance criteria for WP-4

- All type rules covered by unit tests
- Every diagnostic code documented in `docs/diagnostics.md`
- Performance: type-check 1000-line program in under 100ms

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
    AuditSink audit
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

### 7.3 Audit trail

Every computed value records: source rule, source location, inputs consumed, intermediate values, rounding decisions. Serializable as JSON for downstream review.

### 7.4 Acceptance criteria for WP-5

- Golden-file tests for the canonical examples (Section 9) match expected outputs exactly
- Property test: shuffling input order does not change outputs (where order shouldn't matter)
- Re-run determinism test: same program run 1000 times yields identical audit trail hashes

---

## 8. Standard Library Operators (WP-6)

These are first-class language constructs, not user-callable functions. Each is implemented as an AST node with its own evaluator.

| Operator | Surface syntax | Semantics |
|---|---|---|
| Accrue | `accrue <rate> on <basis> using <day_count>` | `basis * rate * yearFraction(period, day_count)` |
| Allocate pro-rata | `allocate <amount> across <set> by <weight_field>` | Sum of weights → distribute proportionally; rounding adjustment to largest |
| Allocate equally | `allocate <amount> across <set> equally` | Equal split with rounding policy |
| Waterfall | `distribute <amount> through waterfall <name>` | Tier-by-tier distribution per named waterfall definition |
| As-of | `<expr> as of <date>` | Evaluate expr against historical state at date |
| Aggregation | `sum of <field> by <dimension>` | Grouped sum; also `weighted average ... weighted by ...` |
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

  let gross_return = nav at end of period - nav at start of period
  let hurdle_amount = nav at start of period * hurdle_rate over period using actual/365
  let excess        = max(0, gross_return - hurdle_amount)
  let above_hwm     = max(0, nav at end of period - high water mark)

  let fee = perf_rate * min(excess, above_hwm)

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

  let gross_assets   = sum of position market value as of valuation date
  let liabilities    = sum of accrued expenses + payables as of valuation date
  let net_assets     = gross_assets - liabilities
  let units          = units outstanding as of valuation date

  let nav_per_unit = net_assets / units

  publish nav as of valuation date
}
```

### 9.5 Equalization (series accounting)

To be authored as part of WP-7 — full equalization with side-pocket carve-outs.

### 9.6 Waterfall distribution

To be authored as part of WP-7 — European-style waterfall with return of capital, preferred return, GP catch-up, and 80/20 split.

### 9.7 Acceptance criteria for WP-7

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
- Money literals: thousands separators with underscores or commas (canonicalize to commas)
- Trailing whitespace stripped, files end with newline

### 10.3 Acceptance criteria for WP-8

- Formatter is idempotent: `format(format(x)) == format(x)` for all valid programs
- All error messages tested via snapshot tests
- Levenshtein-based "did you mean" suggestions for unknown identifiers

---

## 11. LSP Server (WP-9)

### 11.1 Capabilities

- `textDocument/didOpen|didChange` with incremental parsing
- `textDocument/publishDiagnostics` from semantic phase
- `textDocument/completion` — context-aware: keywords, in-scope symbols, schema fields
- `textDocument/hover` — type info, doc strings, evaluated examples
- `textDocument/definition` — go-to-rule, go-to-schedule
- `textDocument/formatting` — invokes the formatter
- `textDocument/codeAction` — apply fix-it hints from diagnostics

### 11.2 Acceptance criteria for WP-9

- Manual smoke test in VS Code with the official LSP4J reference client
- Round-trip latency under 50ms for files under 500 lines
- Completion accuracy: expected symbol appears in top-3 in 95% of test cases

---

## 12. CLI Tools (WP-10)

```
fundflow parse <file.ff>            # Parse and print AST
fundflow check <file.ff>            # Run full semantic analysis
fundflow run <file.ff> --as-of D    # Execute against test fixture
fundflow format <file.ff>           # Format in place
fundflow lsp                        # Start the LSP server on stdio
```

All commands exit with non-zero status on any error. Machine-readable JSON output via `--json` flag.

---

## 13. Schema Extension Mechanism (WP-11)

Customers will need to extend the domain model (custom transaction types, custom fund attributes). Provide a schema definition file:

```fundflow
type extension PrivateEquityFund extends Fund {
  field commitment_period: Period
  field investment_period_end: BusinessDate
  field gp_commitment: Percentage
}
```

The schema is loaded before grammar parsing and registered in the symbol table.

### 13.1 Acceptance criteria for WP-11

- Type extensions resolved during semantic phase
- Existing rules referencing extended fields type-check successfully
- Extensions are versioned alongside the rule modules that use them

---

## 14. AI Agent Integration (WP-12)

### 14.1 Agent contract

The platform's AI agent generates `.ff` programs. Provide:

1. **Grammar artifact** — published copy of `FundFlowLexer.g4` and `FundFlowParser.g4` for grammar-constrained decoding
2. **Type catalog** — JSON schema describing all built-in types and currently registered extensions
3. **Symbol catalog** — JSON listing all in-scope rules, schedules, waterfalls, and ledger accounts for the target fund
4. **Example corpus** — the canonical examples from Section 9 with paired natural-language descriptions
5. **Validate-and-retry endpoint** — POST a candidate program, receive parse/type/eval diagnostics in structured JSON; the agent loop retries with diagnostics in the next prompt until clean

### 14.2 Determinism for the agent

The agent is **forbidden** from inventing identifiers. Every reference must resolve against the symbol catalog. The validator rejects unknown names rather than coercing them.

### 14.3 Acceptance criteria for WP-12

- `POST /v1/dsl/validate` returns `{parse, types, eval}` arrays of diagnostics in under 500ms
- `GET /v1/dsl/catalog?fund_id=X` returns the merged symbol + type catalog for a fund
- An end-to-end test: natural-language prompt → agent generates → validator approves on attempt ≤ 3 for each canonical example

---

## 15. Testing Strategy (cross-cutting)

| Layer | Tool | Coverage target |
|---|---|---|
| Domain types | JUnit + jqwik | 100% line, 95% mutation (Pitest) |
| Grammar | ANTLR test rig + JUnit | 100% rule coverage |
| AST builder | JUnit (round-trip) | 100% node types |
| Semantic | JUnit (snapshot) | 100% diagnostic codes |
| Runtime | Golden-file + property | All canonical examples |
| LSP | Manual + LSP4J test client | All capabilities |

Every module has a `src/test/` tree mirroring `src/main/`. CI runs the full suite on every PR.

---

## 16. Roadmap & Sequencing

| Sprint | Work Packages | Output |
|---|---|---|
| 1 | WP-0 setup, WP-1 domain types | Compilable modules, published types |
| 2 | WP-2 grammar, WP-3 AST | Parse → AST round-trip working |
| 3 | WP-4 semantic | Type-checker green on all examples |
| 4 | WP-5 runtime, WP-6 stdlib | Mgmt fee + NAV examples evaluating |
| 5 | WP-7 examples (all 6) | Full canonical corpus + golden files |
| 6 | WP-8 diagnostics + formatter | Production-quality errors |
| 7 | WP-9 LSP, WP-10 CLI | Editor experience usable |
| 8 | WP-11 extensions, WP-12 agent | Customer customization + agent loop |

Each sprint ends with a runnable demo and an updated `CHANGELOG.md`.

---

## 17. Open Questions for the Project Owner

These need decisions before WP-7 (canonical examples) and WP-11 (extensions):

1. Which day-count conventions beyond the four listed are required for v1? (e.g. `30E/360`, `actual/365L`)
2. Which business calendars must ship in v1? (NYSE, LSE, TARGET2, JPX, custom?)
3. What's the rounding policy for per-investor allocations — bankers' rounding, half-up, or "largest residual"?
4. Are FX rates a static lookup table or pulled from a live source via a `DataSource` interface?
5. What's the canonical bitemporal model — does the engine need to support corrections (known date in the past)?
6. What auth model does the LSP need (stdio only, or remote with mTLS)?

Resolve these before sprint 5.

---

## 18. References to Bake Into the Implementation

- Terence Parr, *The Definitive ANTLR 4 Reference* — grammar patterns
- Martin Fowler, *Domain-Specific Languages* — semantic model and parsing layers
- Bloomberg / industry references for day-count conventions (ISDA documents)
- IFRS 9 and US GAAP for accrual and recognition rules — your eventual rule library will mirror these

---

*End of Specification v0.1*
