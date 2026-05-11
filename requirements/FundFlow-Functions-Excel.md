# FundFlow Functions — Excel Compatibility

**Companion to:** `FundFlow-DSL-Spec-v0.2.md`
**Namespaces covered:** lookup (`lookup.`), info (`info.`), and the empty namespace for Excel-compat
**Implementation modules:** `stdlib/.../functions/lookup/`, `stdlib/.../functions/compat/`

## 1. Goal

A user moving from Excel should find every commonly used function present, with semantics matching Excel within documented tolerance. Excel functions that are inherently non-deterministic, security risks, or fundamentally at odds with the FundFlow type system are **deliberately not implemented** — see §6.

This document covers Lookup, Information, the Excel error model, and the compatibility matrix. Math, Statistical, Financial, DateTime, Text, and Logical Excel-compat functions are listed in their respective companion docs.

## 2. Lookup & Reference

| Function | Signature | Status | Notes |
|---|---|---|---|
| `VLOOKUP` | `VLOOKUP(key, table: R<R>, col_idx: N, exact: B?) → Any` | ★ | Default `exact=TRUE` (Excel default is FALSE; FundFlow is stricter for safety) |
| `HLOOKUP` | `HLOOKUP(key, table: R<R>, row_idx: N, exact: B?) → Any` | ★ | Same `exact` default change |
| `XLOOKUP` | `XLOOKUP(key, lookup_range: R, return_range: R, default?, match_mode: N?, search_mode: N?) → Any` | ★ | Preferred lookup function |
| `LOOKUP` | `LOOKUP(key, lookup_vec: R, result_vec: R?) → Any` | ★ | Vector form only; array form deprecated |
| `INDEX` | `INDEX(range: R, row: N, col: N?) → Any` | ★ | 1-indexed |
| `MATCH` | `MATCH(key, range: R, type: N?) → N` | ★ | type: 1, 0, -1 |
| `XMATCH` | `XMATCH(key, range: R, match_mode: N?, search_mode: N?) → N` | ★ | |
| `CHOOSE` | `CHOOSE(idx: N, ...values) → Any` | ★ | 1-indexed |
| `CHOOSEROWS` | `CHOOSEROWS(range: R, ...row_indices: N) → R` | ★ | |
| `CHOOSECOLS` | `CHOOSECOLS(range: R, ...col_indices: N) → R` | ★ | |
| `FILTER` | `FILTER(range: R, include: R<B>, if_empty?: Any) → R` | ★ | |
| `SORT` | `SORT(range: R, sort_idx: N?, sort_order: N?, by_col: B?) → R` | ★ | |
| `SORTBY` | `SORTBY(range: R, ...by_range_order_pairs) → R` | ★ | |
| `UNIQUE` | `UNIQUE(range: R, by_col: B?, exactly_once: B?) → R` | ★ | |
| `TAKE` | `TAKE(range: R, rows: N, cols: N?) → R` | ★ | |
| `DROP` | `DROP(range: R, rows: N, cols: N?) → R` | ★ | |
| `EXPAND` | `EXPAND(range: R, rows: N, cols: N?, pad?: Any) → R` | ★ | |
| `TOROW` | `TOROW(range: R, ignore: N?, scan_by_col: B?) → R` | ★ | |
| `TOCOL` | `TOCOL(range: R, ignore: N?, scan_by_col: B?) → R` | ★ | |
| `WRAPROWS` | `WRAPROWS(range: R, wrap_count: N, pad?: Any) → R<R>` | ★ | |
| `WRAPCOLS` | `WRAPCOLS(range: R, wrap_count: N, pad?: Any) → R<R>` | ★ | |
| `VSTACK` | `VSTACK(...ranges: R) → R` | ★ | |
| `HSTACK` | `HSTACK(...ranges: R) → R` | ★ | |
| `ROWS` | `ROWS(range: R) → N` | ★ | |
| `COLUMNS` | `COLUMNS(range: R) → N` | ★ | |
| `ROW` / `COLUMN` | not implemented | | Cell-reference based; not meaningful in DSL |
| `OFFSET` | not implemented | | Volatile pointer arithmetic; replaced by `INDEX` |
| `INDIRECT` | not implemented | | String-as-reference is a security and determinism hazard |
| `ADDRESS` / `HYPERLINK` | not implemented | | Workbook-specific concepts |
| `FORMULATEXT` | not implemented | | Inspect a rule's source via tooling, not at runtime |

### 2.1 `XLOOKUP` match modes

- `0` exact match (default; error if not found unless default supplied)
- `-1` exact or next smaller
- `1` exact or next larger
- `2` wildcard match (`*`, `?`, `~` escape)

### 2.2 `XLOOKUP` search modes

- `1` first to last (default)
- `-1` last to first
- `2` binary ascending (range must be sorted)
- `-2` binary descending

### 2.3 Worked examples

```fundflow
// Lookup investor commitment by ID
let commitment = XLOOKUP(investor.id,
                         commitments.investor_id,
                         commitments.amount,
                         USD 0)

// Conditional filter
let us_positions = FILTER(positions, positions.country = "US")

// Sort by market value descending
let top10 = TAKE(SORT(positions, MATCH("market_value", positions.headers, 0), -1), 10)
```

## 3. Information functions

| Function | Signature | Excel | Notes |
|---|---|---|---|
| `ISBLANK` | `ISBLANK(x) → B` | ★ | True if x is absent (Optional.empty) |
| `ISNUMBER` | `ISNUMBER(x) → B` | ★ | True for N or M.amount |
| `ISTEXT` | `ISTEXT(x) → B` | ★ | |
| `ISLOGICAL` | `ISLOGICAL(x) → B` | ★ | |
| `ISERROR` | `ISERROR(x) → B` | ★ | True if x evaluates to an error |
| `ISERR` | `ISERR(x) → B` | ★ | Like ISERROR but excludes #N/A |
| `ISNA` | `ISNA(x) → B` | ★ | True if x is "not available" error |
| `ISEVEN` | `ISEVEN(x: N) → B` | ★ | |
| `ISODD` | `ISODD(x: N) → B` | ★ | |
| `ISREF` | not implemented | | Cell-reference concept |
| `ISFORMULA` | not implemented | | |
| `TYPE` | `TYPE(x) → N` | ★ | 1=N, 2=T, 4=B, 16=error, 64=R, 128=M (FundFlow extension) |
| `N` | `N(x) → N` | ★ | Coerce to number |
| `T` | `T(x) → T` | ★ | Coerce to text |
| `CELL` | not implemented | | Cell-reference concept |
| `INFO` | not implemented | | Workbook-specific |
| `SHEET` / `SHEETS` | not implemented | | |

### 3.1 FundFlow-specific information functions

| Function | Signature | Notes |
|---|---|---|
| `IS_MONEY` | `IS_MONEY(x) → B` | True iff x is Money |
| `IS_PERCENT` | `IS_PERCENT(x) → B` | |
| `IS_DATE` | `IS_DATE(x) → B` | |
| `IS_PERIOD` | `IS_PERIOD(x) → B` | |
| `IS_BUSINESS_DAY` | `IS_BUSINESS_DAY(d: D, cal: T?) → B` | Defaults to context calendar |
| `CURRENCY_OF` | `CURRENCY_OF(m: M) → T` | "USD", "EUR", … |
| `AMOUNT_OF` | `AMOUNT_OF(m: M) → N` | Strips currency |
| `DAYCOUNT_NAME` | `DAYCOUNT_NAME(dc: DayCount) → T` | "actual/365", … |

## 4. Excel error model

FundFlow does **not** have inline error values like `#N/A`, `#DIV/0!`, `#VALUE!`. Instead:

- Functions that would return an Excel error throw a typed runtime exception (`FundFlowEvaluationException` with a typed cause)
- `IFERROR(expr, fallback)` and `IFNA(expr, fallback)` catch these exceptions and return the fallback
- This preserves Excel migration ergonomics while keeping the type system honest

### 4.1 Mapping table

| Excel error | FundFlow exception subclass | Trigger examples |
|---|---|---|
| `#DIV/0!` | `DivisionByZeroException` | Division or MOD by zero |
| `#N/A` | `NotAvailableException` | VLOOKUP/MATCH miss with no default |
| `#VALUE!` | `TypeMismatchException` | Wrong argument type |
| `#REF!` | `BrokenReferenceException` | Missing entity in DataSource |
| `#NUM!` | `NumericException` | Domain error (e.g. SQRT of negative) or non-convergence (IRR) |
| `#NAME?` | (caught at compile time) | Unknown function/identifier |
| `#NULL!` | (not applicable) | Cell-intersection concept |
| `#GETTING_DATA` | (not applicable) | Volatile/external data not used |
| `#SPILL!` | (not applicable) | Spill semantics not used |
| `#CALC!` | `CalculationException` | Generic catch-all |

### 4.2 `IFERROR` / `IFNA`

```fundflow
// Provide default when lookup misses
let region = IFERROR(VLOOKUP(investor.id, regions, 2, TRUE), "Unknown")

// Specifically handle N/A
let yield = IFNA(YIELD(...), 0)
```

### 4.3 `ERROR.TYPE`

```fundflow
ERROR.TYPE(expr) → N
// Returns 1=#NULL!, 2=#DIV/0!, 3=#VALUE!, 4=#REF!, 5=#NAME?, 6=#NUM!, 7=#N/A, 8=#GETTING_DATA
```

In FundFlow this returns the numeric code matching the Excel value if `expr` evaluates to an error, else `#N/A`.

## 5. Compatibility matrix

The full Excel function inventory is tracked in `docs/excel-compatibility-matrix.md`, auto-generated from the registry. The columns:

| Column | Meaning |
|---|---|
| Excel name | Excel function name |
| FundFlow name | Identical, or `(replaced by ...)`, or `(not implemented)` |
| Status | Implemented / Replaced / Not implemented |
| Doc reference | Companion doc and section |
| Parity tolerance | e.g. `exact`, `1e-10`, `monetary HALF_UP at scale 2` |
| Rationale | For "Not implemented" rows |

A summary count is auto-published at the top of the matrix. Target for v1: ≥ 90% of Excel functions implemented across all categories.

## 6. Deliberately not implemented (with rationale)

| Excel function | Reason |
|---|---|
| `NOW`, `TODAY` | Non-deterministic. Use `AS_OF_DATE()` or `VALUATION_DATE()` instead. |
| `RAND`, `RANDBETWEEN`, `RANDARRAY` | Non-deterministic. Fund accounting must be reproducible. |
| `OFFSET` | Volatile. Pointer arithmetic at runtime is incompatible with auditability. Use `INDEX`. |
| `INDIRECT` | String-as-reference is a security hazard and breaks static analysis. |
| `ADDRESS`, `HYPERLINK` | Cell/workbook concepts not present in DSL. |
| `ROW`, `COLUMN`, `CELL`, `INFO`, `SHEET`, `SHEETS` | Cell/workbook concepts. |
| `FORMULATEXT` | Source inspection is a tooling concern, not runtime. |
| `WEBSERVICE`, `FILTERXML`, `ENCODEURL` | Network access at runtime would break determinism and tenancy. |
| `IMAGE`, `STOCKHISTORY` | External data + non-determinism. |
| `LAMBDA`, `LET`, `MAP`, `REDUCE`, `BYROW`, `BYCOL`, `MAKEARRAY`, `SCAN`, `ISOMITTED` | User-defined first-class functions are out of scope for v1. May be reconsidered for v2 with strict purity rules. |
| `GETPIVOTDATA` | PivotTable-specific. |
| Database functions (`DSUM`, `DAVERAGE`, `DCOUNT`, etc.) | Replaced by `SUMIFS`, `AVERAGEIFS`, `COUNTIFS` which cover the same use cases more cleanly. |

If a customer requires any of these, they should be revisited as platform-level features outside the DSL (e.g. data-loading hooks for external data, tooling for source inspection).

## 7. Excel-import tool

`fundflow excel-import <file.xlsx>` performs best-effort conversion of cell formulas to FundFlow source.

### 7.1 What it converts well

- Simple arithmetic and aggregation (`SUM`, `AVERAGE`, `MIN`, `MAX`)
- Standard lookups (`VLOOKUP`, `XLOOKUP`, `MATCH`/`INDEX`)
- Most date/time functions
- Most financial functions
- Conditional logic (`IF`, `IFS`, `IFERROR`, `IFNA`)

### 7.2 What requires manual review

- Sheet-cross references (converted to TODO comments)
- `OFFSET`/`INDIRECT` (flagged with rewrite suggestions)
- Volatile functions (replaced with deterministic equivalents and a warning)
- Array formulas (best-effort to dynamic-array equivalents)
- Named ranges (mapped to FundFlow named bindings; user must confirm mapping)

### 7.3 Output

For each formula, the tool produces:

```
// from Sheet1!B7: =VLOOKUP(A7,Investors!$A$2:$D$500,3,FALSE)
let region = VLOOKUP(investor_id, investors_table, 3, TRUE)
```

with original formula in a comment for traceability. A separate `excel-import-report.json` lists every conversion with status (clean / warning / manual).

## 8. Acceptance criteria

- All ★ functions in §2 and §3 implemented
- Excel error model exceptions thrown with correct subclass for each trigger
- `ERROR.TYPE` returns correct numeric codes
- Compatibility matrix auto-generated and committed
- Excel-import tool passes a corpus of 100 representative formulas with documented conversion status (≥ 80 clean, ≥ 95 with warnings or better)
- Parity test fixtures exist for every ★ function

---

*End of Excel Compatibility Reference.*
