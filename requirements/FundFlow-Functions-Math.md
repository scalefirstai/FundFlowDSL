# FundFlow Functions — Math

**Companion to:** `FundFlow-DSL-Spec-v0.2.md`
**Namespace:** `math.` (with Excel-compatible names also in the empty namespace)
**Implementation module:** `stdlib/src/main/java/ai/getfundflow/dsl/stdlib/functions/math/`

## Conventions

- `N` = Number (BigDecimal). All math is BigDecimal-based; no double/float.
- `R<T>` = Range of T
- `?` after a type = optional parameter
- `...T` = variadic of type T
- ★ in the Excel column = Excel-compatible (matches Excel semantics within documented tolerance)
- Default `MathContext` is `DECIMAL64` for intermediates. Output rounding is per-function.
- Transcendental functions (`LN`, `EXP`, `POWER` with non-integer exponent, trig) use `ch.obermuhlner:big-math`.

## 1. Basic arithmetic & rounding

| Function | Signature | Excel | Notes |
|---|---|---|---|
| `ABS` | `ABS(x: N) → N` | ★ | Absolute value |
| `SIGN` | `SIGN(x: N) → N` | ★ | -1, 0, or 1 |
| `ROUND` | `ROUND(x: N, digits: N) → N` | ★ | HALF_UP rounding |
| `ROUNDUP` | `ROUNDUP(x: N, digits: N) → N` | ★ | Away from zero |
| `ROUNDDOWN` | `ROUNDDOWN(x: N, digits: N) → N` | ★ | Toward zero |
| `MROUND` | `MROUND(x: N, multiple: N) → N` | ★ | Round to nearest multiple |
| `CEILING` | `CEILING(x: N, sig: N) → N` | ★ | Round up to multiple of sig |
| `CEILING.MATH` | `CEILING.MATH(x: N, sig: N?, mode: N?) → N` | ★ | Mode controls negative rounding |
| `FLOOR` | `FLOOR(x: N, sig: N) → N` | ★ | Round down to multiple of sig |
| `FLOOR.MATH` | `FLOOR.MATH(x: N, sig: N?, mode: N?) → N` | ★ | |
| `TRUNC` | `TRUNC(x: N, digits: N?) → N` | ★ | Truncate (default 0 digits) |
| `INT` | `INT(x: N) → N` | ★ | Floor to integer |
| `EVEN` | `EVEN(x: N) → N` | ★ | Round up (away from 0) to even |
| `ODD` | `ODD(x: N) → N` | ★ | Round up (away from 0) to odd |
| `MOD` | `MOD(x: N, divisor: N) → N` | ★ | Excel modulo (sign of divisor) |
| `QUOTIENT` | `QUOTIENT(x: N, y: N) → N` | ★ | Integer quotient |
| `math.bankers_round` | `math.bankers_round(x: N, digits: N) → N` | | HALF_EVEN — preferred for monetary settlement |

**Edge cases:**
- `MOD` with divisor 0 raises `FF2010 division by zero`
- `ROUND(x, 0)` returns integer-valued BigDecimal with scale 0
- Negative `digits` rounds at left of decimal point (`ROUND(1234.5, -2) = 1200`)

## 2. Powers, roots, logarithms

| Function | Signature | Excel | Notes |
|---|---|---|---|
| `POWER` | `POWER(base: N, exp: N) → N` | ★ | Integer exp uses BigDecimal.pow; fractional uses big-math |
| `SQRT` | `SQRT(x: N) → N` | ★ | Error if x < 0 (`FF2011 domain error`) |
| `EXP` | `EXP(x: N) → N` | ★ | e^x |
| `LN` | `LN(x: N) → N` | ★ | Natural log; error if x ≤ 0 |
| `LOG` | `LOG(x: N, base: N?) → N` | ★ | Default base 10 |
| `LOG10` | `LOG10(x: N) → N` | ★ | |
| `FACT` | `FACT(n: N) → N` | ★ | Factorial; error if n < 0 or non-integer |
| `FACTDOUBLE` | `FACTDOUBLE(n: N) → N` | ★ | Double factorial |
| `GCD` | `GCD(...n: N) → N` | ★ | Greatest common divisor (variadic, ≥ 1 arg) |
| `LCM` | `LCM(...n: N) → N` | ★ | Least common multiple |
| `COMBIN` | `COMBIN(n: N, k: N) → N` | ★ | Combinations (n choose k) |
| `COMBINA` | `COMBINA(n: N, k: N) → N` | ★ | Combinations with repetition |
| `PERMUT` | `PERMUT(n: N, k: N) → N` | ★ | Permutations |
| `PERMUTATIONA` | `PERMUTATIONA(n: N, k: N) → N` | ★ | Permutations with repetition |
| `MULTINOMIAL` | `MULTINOMIAL(...n: N) → N` | ★ | |
| `SQRTPI` | `SQRTPI(x: N) → N` | ★ | √(x·π) |

## 3. Trigonometry & hyperbolic

All Excel-compatible. Implemented via big-math with configurable precision (default DECIMAL64).

| Function | Signature | Excel |
|---|---|---|
| `PI` | `PI() → N` | ★ |
| `SIN` | `SIN(x: N) → N` | ★ |
| `COS` | `COS(x: N) → N` | ★ |
| `TAN` | `TAN(x: N) → N` | ★ |
| `ASIN` | `ASIN(x: N) → N` | ★ |
| `ACOS` | `ACOS(x: N) → N` | ★ |
| `ATAN` | `ATAN(x: N) → N` | ★ |
| `ATAN2` | `ATAN2(x: N, y: N) → N` | ★ |
| `SINH` | `SINH(x: N) → N` | ★ |
| `COSH` | `COSH(x: N) → N` | ★ |
| `TANH` | `TANH(x: N) → N` | ★ |
| `ASINH` | `ASINH(x: N) → N` | ★ |
| `ACOSH` | `ACOSH(x: N) → N` | ★ |
| `ATANH` | `ATANH(x: N) → N` | ★ |
| `CSC` / `SEC` / `COT` | reciprocals | ★ |
| `CSCH` / `SECH` / `COTH` | hyperbolic reciprocals | ★ |
| `DEGREES` | `DEGREES(rad: N) → N` | ★ |
| `RADIANS` | `RADIANS(deg: N) → N` | ★ |

Trig is rare in fund accounting but included for parity with Excel.

## 4. Aggregates over ranges

These are AGGREGATE-tagged and operate on `Range<N>`, `Range<M>` (Money), or variadic mixed.

| Function | Signature | Excel | Notes |
|---|---|---|---|
| `SUM` | `SUM(...x: N \| R<N>) → N` | ★ | Mixed scalars and ranges |
| `SUM` (overload) | `SUM(...x: M \| R<M>) → M` | ★ | All Money same currency |
| `SUMIF` | `SUMIF(range: R, criterion: T, sum_range: R<N>?) → N` | ★ | |
| `SUMIFS` | `SUMIFS(sum_range: R<N>, ...range_crit_pairs) → N` | ★ | Variadic pairs |
| `SUMPRODUCT` | `SUMPRODUCT(...ranges: R<N>) → N` | ★ | Element-wise product then sum; ranges must be same length |
| `PRODUCT` | `PRODUCT(...x: N \| R<N>) → N` | ★ | |
| `SUMSQ` | `SUMSQ(...x: N \| R<N>) → N` | ★ | Sum of squares |
| `SUMX2MY2` | `SUMX2MY2(x: R<N>, y: R<N>) → N` | ★ | Σ(xᵢ² − yᵢ²) |
| `SUMX2PY2` | `SUMX2PY2(x: R<N>, y: R<N>) → N` | ★ | Σ(xᵢ² + yᵢ²) |
| `SUMXMY2` | `SUMXMY2(x: R<N>, y: R<N>) → N` | ★ | Σ(xᵢ − yᵢ)² |
| `SERIESSUM` | `SERIESSUM(x: N, n: N, m: N, coeffs: R<N>) → N` | ★ | Power series |

**Empty range behavior:**
- `SUM(empty) = 0`
- `PRODUCT(empty) = 1`
- `SUMPRODUCT` of empty ranges = 0

**Currency rules:**
- Money overloads require all elements to share the same currency; mixed-currency raises `FF2020 currency mismatch in aggregate`
- Use `finance.fx_convert` upstream to normalize before aggregation

## 5. Min / Max

(Listed here because they're math; also referenced from Statistical.)

| Function | Signature | Excel |
|---|---|---|
| `MIN` | `MIN(...x: N \| R<N>) → N` | ★ |
| `MIN` (Money overload) | `MIN(...x: M \| R<M>) → M` | ★ |
| `MAX` | `MAX(...x: N \| R<N>) → N` | ★ |
| `MAX` (Money overload) | `MAX(...x: M \| R<M>) → M` | ★ |
| `MINA` | `MINA(...) → N` | ★ | Counts logicals as 0/1 |
| `MAXA` | `MAXA(...) → N` | ★ | |
| `MINIFS` | `MINIFS(min_range, ...range_crit_pairs) → N` | ★ | |
| `MAXIFS` | `MAXIFS(max_range, ...range_crit_pairs) → N` | ★ | |

**Empty range:** `MIN(empty)` and `MAX(empty)` raise `FF2030 empty aggregate input`. Excel returns 0 here; FundFlow is stricter to catch ops mistakes.

## 6. Number conversion & parsing

| Function | Signature | Excel |
|---|---|---|
| `DECIMAL` | `DECIMAL(text: T, radix: N) → N` | ★ Convert text in given base to decimal |
| `BASE` | `BASE(n: N, radix: N, min_length: N?) → T` | ★ Convert decimal to text in base |
| `ARABIC` | `ARABIC(roman: T) → N` | ★ |
| `ROMAN` | `ROMAN(n: N, form: N?) → T` | ★ |
| `BIN2DEC` / `BIN2HEX` / `BIN2OCT` | base conversions | ★ |
| `DEC2BIN` / `DEC2HEX` / `DEC2OCT` | base conversions | ★ |
| `HEX2BIN` / `HEX2DEC` / `HEX2OCT` | | ★ |
| `OCT2BIN` / `OCT2DEC` / `OCT2HEX` | | ★ |

Mostly for Excel parity — rarely needed in fund accounting but harmless to include.

## 7. Matrix functions

Implemented for completeness; AGGREGATE-tagged. Use Apache Commons Math under the hood with BigDecimal adapter.

| Function | Signature | Excel | Notes |
|---|---|---|---|
| `MMULT` | `MMULT(a: R<R<N>>, b: R<R<N>>) → R<R<N>>` | ★ | Matrix multiplication |
| `MINVERSE` | `MINVERSE(m: R<R<N>>) → R<R<N>>` | ★ | Matrix inverse |
| `MDETERM` | `MDETERM(m: R<R<N>>) → N` | ★ | Determinant |
| `MUNIT` | `MUNIT(n: N) → R<R<N>>` | ★ | Identity matrix |
| `TRANSPOSE` | `TRANSPOSE(r: R<T>) → R<T>` | ★ | Works on any range |

## 8. Worked examples

```fundflow
// Round NAV per unit to 4 decimals (HALF_UP)
let nav_per_unit = ROUND(net_assets / units, 4)

// Round to nearest cent using bankers' rounding (HALF_EVEN)
let payable = math.bankers_round(fee_amount.value, 2)

// Aggregate position market values
let gva = SUM(positions.market_value)

// Conditional aggregate
let us_assets = SUMIFS(positions.market_value,
                       positions.country, "US",
                       positions.asset_class, "Equity")

// Power series for compound growth
let fv = SERIESSUM(1.05, 0, 1, [1, 1, 1, 1, 1])  // 5 periods at 5%

// Effective rate from periodic
let effective = POWER(1 + periodic_rate, periods) - 1
```

## 9. Acceptance criteria

- All functions in §1–§7 implemented in `stdlib/.../functions/math/`
- Each function has ≥3 unit tests: happy path, edge case, error case
- Property tests:
  - `ABS(x) ≥ 0` for all x
  - `SUM` is commutative under shuffle
  - `PRODUCT(SUM order doesn't change)` within DECIMAL64 precision
  - `POWER(SQRT(x), 2) ≈ x` within tolerance for x ≥ 0
- Excel parity test fixture for every function tagged ★, with inputs covering: positive, negative, zero, fractional, large, very small, boundary values
- Performance: any single function call returns in < 5ms for non-iterative cases

## 10. Implementation notes

- **`POWER` for non-integer exponents:** delegate to big-math `BigDecimalMath.pow(base, exponent, mathContext)`. For integer exponents use `BigDecimal.pow(int)` for speed.
- **Trig precision:** big-math defaults are sufficient. Document precision in Javadoc.
- **`MOD` semantics:** Excel uses `n - d * INT(n/d)` (sign follows divisor). Java's `%` doesn't match — implement explicitly.
- **`SUMPRODUCT` with mismatched range lengths:** error `FF2031`, not silent truncation.
- **Range argument coercion:** scalars promote to single-element ranges in aggregate functions.
- **Money + Number ambiguity:** `SUM(USD 1, 2)` is an error — must be `SUM(USD 1, USD 2)` or `SUM(1, 2)`.

---

*End of Math Functions Reference.*
