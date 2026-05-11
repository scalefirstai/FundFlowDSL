# FundFlow Functions — Financial

**Companion to:** `FundFlow-DSL-Spec-v0.2.md`
**Namespace:** `finance.` (with Excel-compatible names also in the empty namespace)
**Implementation module:** `stdlib/.../functions/financial/`

These are the workhorses for fund admin. Implemented with `BigDecimal` throughout. **Excel parity tests required for every function tagged ★.**

## 1. v1 mandatory function set

If you can only ship a subset for v1, ship these. Everything else can defer.

**Time value of money:** `PV`, `FV`, `PMT`, `IPMT`, `PPMT`, `NPER`, `RATE`, `EFFECT`, `NOMINAL`
**IRR / NPV:** `NPV`, `XNPV`, `IRR`, `XIRR`, `MIRR`, `finance.MOIC`, `finance.TVPI`, `finance.DPI`, `finance.RVPI`
**Day count:** `YEARFRAC`, `DAYS360`, `EDATE`, `EOMONTH`, `WORKDAY`, `NETWORKDAYS`
**Bond pricing (basic):** `PRICE`, `YIELD`, `DURATION`, `MDURATION`, `ACCRINT`, `ACCRINTM`
**FundFlow-specific:** `finance.fx_convert`, `finance.discount_factor`, `finance.compound`, `finance.simple_interest`

Bond coupon-date math (`COUPDAYS` family), advanced T-bill, depreciation, and exotic conventions are §6+ and can ship in subsequent sprints.

## 2. Time value of money

Excel TVM convention: positive cashflows are inflows to the holder, negative are outflows. FundFlow follows this. The `type` parameter is 0 (end of period, default) or 1 (beginning of period).

| Function | Signature | Excel | Notes |
|---|---|---|---|
| `PV` | `PV(rate: P, nper: N, pmt: M, fv: M?, type: N?) → M` | ★ | Present value |
| `FV` | `FV(rate: P, nper: N, pmt: M, pv: M?, type: N?) → M` | ★ | Future value |
| `FVSCHEDULE` | `FVSCHEDULE(principal: M, rates: R<P>) → M` | ★ | Variable rate FV |
| `PMT` | `PMT(rate: P, nper: N, pv: M, fv: M?, type: N?) → M` | ★ | Periodic payment |
| `IPMT` | `IPMT(rate: P, per: N, nper: N, pv: M, fv: M?, type: N?) → M` | ★ | Interest portion |
| `PPMT` | `PPMT(rate: P, per: N, nper: N, pv: M, fv: M?, type: N?) → M` | ★ | Principal portion |
| `CUMIPMT` | `CUMIPMT(rate, nper, pv, start_per, end_per, type) → M` | ★ | Cumulative interest |
| `CUMPRINC` | `CUMPRINC(rate, nper, pv, start_per, end_per, type) → M` | ★ | Cumulative principal |
| `ISPMT` | `ISPMT(rate, per, nper, pv) → M` | ★ | Interest on straight-line |
| `NPER` | `NPER(rate, pmt, pv, fv?, type?) → N` | ★ | Number of periods |
| `RATE` | `RATE(nper, pmt, pv, fv?, type?, guess?) → P` | ★ ITERATIVE | Newton's method |
| `EFFECT` | `EFFECT(nominal: P, npery: N) → P` | ★ | Effective annual rate |
| `NOMINAL` | `NOMINAL(effect: P, npery: N) → P` | ★ | Nominal annual rate |
| `RRI` | `RRI(nper: N, pv: M, fv: M) → P` | ★ | Equivalent interest rate |

### 2.1 Worked examples

```fundflow
// Bond present value at 5% over 10 years with 1000 face
let bond_pv = PV(rate = 5%, nper = 10, pmt = USD 50, fv = USD 1000)

// Future value of a series of investments
let retirement = FV(rate = 7%, nper = 30, pmt = USD -10000)

// Variable-rate FV (capital call schedule with stepped returns)
let final_value = FVSCHEDULE(USD 1_000_000, [3%, 5%, 7%, 8%])
```

### 2.2 Iterative-method defaults

`RATE`, `IRR`, `XIRR`, `MIRR`, `YIELD`, `YIELDDISC`, `YIELDMAT` use Newton-Raphson with:
- `maxIterations = 100` (configurable per call via runtime context)
- `tolerance = 1e-10`
- Initial guess defaults to `0.10` (10%) unless specified

Non-convergence raises `FF2050 iterative method did not converge` with diagnostic info (best estimate, iterations used, residual). The error message includes a suggestion to provide a better `guess`.

## 3. IRR / NPV family

These are the workhorses for PE/VC/private credit fund admin. Test rigorously against Excel.

| Function | Signature | Excel | Notes |
|---|---|---|---|
| `NPV` | `NPV(rate: P, ...cashflows: M \| R<M>) → M` | ★ | **Excel semantics:** first cf at t=1, not t=0 |
| `XNPV` | `XNPV(rate: P, cashflows: R<M>, dates: R<D>) → M` | ★ | Date-aware; uses actual/365 |
| `IRR` | `IRR(cashflows: R<M>, guess: P?) → P` | ★ ITERATIVE | First cf at t=0 (Excel semantics) |
| `XIRR` | `XIRR(cashflows: R<M>, dates: R<D>, guess: P?) → P` | ★ ITERATIVE | Date-aware IRR |
| `MIRR` | `MIRR(cashflows: R<M>, finance_rate: P, reinvest_rate: P) → P` | ★ | Modified IRR |
| `finance.MOIC` | `finance.MOIC(distributions: R<M>, contributions: R<M>) → N` | | Multiple of invested capital = ΣD / ΣC |
| `finance.TVPI` | `finance.TVPI(distributions: R<M>, nav: M, contributions: R<M>) → N` | | (ΣD + NAV) / ΣC |
| `finance.DPI` | `finance.DPI(distributions: R<M>, contributions: R<M>) → N` | | ΣD / ΣC |
| `finance.RVPI` | `finance.RVPI(nav: M, contributions: R<M>) → N` | | NAV / ΣC |
| `finance.PIC` | `finance.PIC(contributions: R<M>, commitment: M) → P` | | Paid-in to committed |
| `finance.NET_IRR` | `finance.NET_IRR(cashflows: R<M>, dates: R<D>, fees: R<M>?, guess: P?) → P` | | XIRR after fee netting |
| `finance.GROSS_IRR` | `finance.GROSS_IRR(cashflows: R<M>, dates: R<D>, guess: P?) → P` | | Same as XIRR; alias for clarity |

### 3.1 Excel `NPV` quirk

`NPV(rate, cashflows)` in Excel discounts the first cashflow by one period. To compute the standard finance NPV (first cashflow at t=0), use:

```fundflow
let true_npv = cashflows[0] + NPV(rate, cashflows[1..])
// or use XNPV which is date-aware and unambiguous
let xnpv_value = XNPV(rate, cashflows, dates)
```

This quirk is documented loudly in the function metadata. The LSP hover for `NPV` includes a warning about this.

### 3.2 Worked example: PE fund metrics

```fundflow
rule "PE Fund Performance Metrics" {
  description: "Standard PE fund metrics: IRR, MOIC, TVPI, DPI, RVPI"
  applies to: fund "Beta PE Fund III"

  let cf       = fund.cashflows ordered by date
  let dates    = fund.cashflow_dates
  let dist     = fund.distributions
  let contrib  = fund.contributions
  let nav      = fund.nav as of valuation date

  let irr  = XIRR(cf, dates)
  let moic = finance.MOIC(dist, contrib)
  let tvpi = finance.TVPI(dist, nav, contrib)
  let dpi  = finance.DPI(dist, contrib)
  let rvpi = finance.RVPI(nav, contrib)

  publish irr, moic, tvpi, dpi, rvpi as fund metrics
}
```

## 4. Bond pricing & yields

All Excel-compatible. Day-count `basis` parameter follows Excel convention:

- `0` = US 30/360 (default in Excel)
- `1` = actual/actual
- `2` = actual/360
- `3` = actual/365
- `4` = European 30/360

| Function | Signature | Excel |
|---|---|---|
| `PRICE` | `PRICE(settlement: D, maturity: D, rate: P, yld: P, redemption: N, frequency: N, basis: N?) → N` | ★ |
| `YIELD` | `YIELD(settlement, maturity, rate, price, redemption, frequency, basis?) → P` | ★ ITERATIVE |
| `DURATION` | `DURATION(settlement, maturity, coupon, yld, frequency, basis?) → N` | ★ |
| `MDURATION` | `MDURATION(settlement, maturity, coupon, yld, frequency, basis?) → N` | ★ |
| `ACCRINT` | `ACCRINT(issue, first_interest, settlement, rate, par, frequency, basis?, calc_method?) → M` | ★ |
| `ACCRINTM` | `ACCRINTM(issue, settlement, rate, par, basis?) → M` | ★ |
| `DISC` | `DISC(settlement, maturity, price, redemption, basis?) → P` | ★ |
| `INTRATE` | `INTRATE(settlement, maturity, investment, redemption, basis?) → P` | ★ |
| `RECEIVED` | `RECEIVED(settlement, maturity, investment, discount, basis?) → M` | ★ |
| `YIELDDISC` | `YIELDDISC(settlement, maturity, price, redemption, basis?) → P` | ★ |
| `YIELDMAT` | `YIELDMAT(settlement, maturity, issue, rate, price, basis?) → P` | ★ |
| `PRICEDISC` | `PRICEDISC(settlement, maturity, discount, redemption, basis?) → N` | ★ |
| `PRICEMAT` | `PRICEMAT(settlement, maturity, issue, rate, yld, basis?) → N` | ★ |
| `ODDFPRICE` | first/last odd period price | ★ |
| `ODDFYIELD` | first/last odd period yield | ★ |
| `ODDLPRICE` | last odd period price | ★ |
| `ODDLYIELD` | last odd period yield | ★ |

### 4.1 Bond coupon date math

| Function | Signature | Excel |
|---|---|---|
| `COUPDAYS` | `COUPDAYS(settlement, maturity, frequency, basis?) → N` | ★ |
| `COUPDAYBS` | `COUPDAYBS(settlement, maturity, frequency, basis?) → N` | ★ |
| `COUPDAYSNC` | `COUPDAYSNC(settlement, maturity, frequency, basis?) → N` | ★ |
| `COUPNCD` | `COUPNCD(settlement, maturity, frequency, basis?) → D` | ★ |
| `COUPPCD` | `COUPPCD(settlement, maturity, frequency, basis?) → D` | ★ |
| `COUPNUM` | `COUPNUM(settlement, maturity, frequency, basis?) → N` | ★ |

### 4.2 T-bill conventions

| Function | Signature | Excel |
|---|---|---|
| `TBILLEQ` | `TBILLEQ(settlement, maturity, discount) → P` | ★ |
| `TBILLPRICE` | `TBILLPRICE(settlement, maturity, discount) → N` | ★ |
| `TBILLYIELD` | `TBILLYIELD(settlement, maturity, price) → P` | ★ |

### 4.3 Dollar pricing

| Function | Signature | Excel |
|---|---|---|
| `DOLLARDE` | `DOLLARDE(fractional: N, fraction: N) → N` | ★ |
| `DOLLARFR` | `DOLLARFR(decimal: N, fraction: N) → N` | ★ |

## 5. Depreciation

For fund-level fixed-asset accounting and real-asset funds.

| Function | Signature | Excel | Notes |
|---|---|---|---|
| `SLN` | `SLN(cost: M, salvage: M, life: N) → M` | ★ | Straight-line |
| `DB` | `DB(cost, salvage, life, period, month?) → M` | ★ | Fixed declining balance |
| `DDB` | `DDB(cost, salvage, life, period, factor?) → M` | ★ | Double declining balance |
| `SYD` | `SYD(cost, salvage, life, per) → M` | ★ | Sum-of-years digits |
| `VDB` | `VDB(cost, salvage, life, start_per, end_per, factor?, no_switch?) → M` | ★ | Variable declining |
| `AMORDEGRC` | `AMORDEGRC(cost, date_purchased, first_period, salvage, period, rate, basis?) → M` | ★ | French linear |
| `AMORLINC` | `AMORLINC(...)` | ★ | French linear, prorated |

## 6. Day-count and date math (financial)

| Function | Signature | Excel | Notes |
|---|---|---|---|
| `YEARFRAC` | `YEARFRAC(start: D, end: D, basis: N?) → N` | ★ | Basis 0–4 as defined above |
| `finance.year_fraction` | `finance.year_fraction(start: D, end: D, day_count: DayCount) → N` | | Native FundFlow form using DayCount type |
| `DAYS360` | `DAYS360(start: D, end: D, method: B?) → N` | ★ | |
| `EDATE` | `EDATE(start: D, months: N) → D` | ★ | |
| `EOMONTH` | `EOMONTH(start: D, months: N) → D` | ★ | |
| `WORKDAY` | `WORKDAY(start: D, days: N, holidays: R<D>?) → D` | ★ | |
| `WORKDAY.INTL` | `WORKDAY.INTL(start, days, weekend?, holidays?) → D` | ★ | |
| `NETWORKDAYS` | `NETWORKDAYS(start, end, holidays?) → N` | ★ | |
| `NETWORKDAYS.INTL` | with weekend pattern | ★ | |
| `DATEDIF` | `DATEDIF(start: D, end: D, unit: T) → N` | ★ | unit: "Y", "M", "D", "MD", "YM", "YD" |

These are also in `FundFlow-Functions-DateTime.md` — listed here for convenience because they're financial workhorses.

## 7. FundFlow-only finance functions

These have no Excel equivalent.

| Function | Signature | Notes |
|---|---|---|
| `finance.fx_convert` | `finance.fx_convert(amount: M, to_currency: T, as_of: D) → M` | Uses `DataSource.fxRate` |
| `finance.fx_rate` | `finance.fx_rate(from: T, to: T, as_of: D) → N` | Returns the rate, not the converted amount |
| `finance.discount_factor` | `finance.discount_factor(rate: P, period: Pd, day_count: DayCount) → N` | exp(-rate * yearFrac) for continuous; (1+rate)^-yearFrac for discrete |
| `finance.compound` | `finance.compound(principal: M, rate: P, period: Pd, frequency: T, day_count: DayCount) → M` | frequency: "annual", "semi", "quarterly", "monthly", "daily", "continuous" |
| `finance.simple_interest` | `finance.simple_interest(principal: M, rate: P, period: Pd, day_count: DayCount) → M` | |
| `finance.accrued_interest` | `finance.accrued_interest(face: M, coupon: P, last_coupon: D, settlement: D, frequency: N, day_count: DayCount) → M` | Bond accrued interest |
| `finance.clean_price` | `finance.clean_price(dirty: N, accrued: M, face: M) → N` | Strip accrued from dirty price |
| `finance.dirty_price` | `finance.dirty_price(clean: N, accrued: M, face: M) → N` | |
| `finance.zero_curve_df` | `finance.zero_curve_df(curve: T, tenor: Pd, as_of: D) → N` | Discount factor from named yield curve |
| `finance.par_yield` | `finance.par_yield(zero_rates: R<P>, tenors: R<Pd>) → P` | Par yield from zero curve |
| `finance.bond_price_from_yield` | `finance.bond_price_from_yield(face, coupon, yield, settlement, maturity, frequency, day_count) → M` | Native form, uses DayCount type |
| `finance.bond_yield_from_price` | inverse of above | ITERATIVE |

### 7.1 Worked example: bond accrued interest

```fundflow
rule "Bond Accrued Interest at Settlement" {
  description: "Compute accrued interest for a fixed-rate bond at settlement"

  let face         = USD 10,000,000
  let coupon       = 4.5%
  let last_coupon  = 2026-01-15
  let settlement   = 2026-04-30
  let frequency    = 2  // semiannual
  let day_count    = 30/360

  let accrued = finance.accrued_interest(face, coupon, last_coupon, settlement, frequency, day_count)
  let clean   = USD 102.50
  let dirty   = finance.dirty_price(clean.value, accrued, face)

  post accrued to ledger account "Bond Accrued Interest Receivable"
}
```

## 8. Fee and waterfall functions

These overlap with the Fund Accounting domain doc but the underlying calculations are financial. Listed here for visibility; full ops in `FundFlow-Functions-FundAccounting.md`.

| Function | Signature | Notes |
|---|---|---|
| `finance.management_fee` | `finance.management_fee(basis: M, rate: P, period: Pd, day_count: DayCount) → M` | Standard accrual |
| `finance.performance_fee` | `finance.performance_fee(gross_return: M, basis: M, hurdle: P, perf_rate: P, hwm: M?, day_count: DayCount?, period: Pd) → M` | Hurdle + HWM optional |
| `finance.preferred_return` | `finance.preferred_return(contributions: R<M>, dates: R<D>, rate: P, as_of: D, day_count: DayCount) → M` | Compounded preferred return |
| `finance.catch_up` | `finance.catch_up(remaining: M, lp_share: P, gp_share: P) → (M, M)` | Tuple: (LP catch-up, GP catch-up) |

## 9. Acceptance criteria

- v1 mandatory set (§1) implemented with full Excel parity tests
- All ITERATIVE functions converge for the canonical test corpus
- Non-convergence produces actionable error message
- Bond functions tested against Bloomberg-published examples for at least 5 real bonds across day-count conventions
- IRR/XIRR matches Excel to within `1e-9` on the standard test corpus (50 representative cashflow series)
- All currency arithmetic preserves currency through the calculation chain
- FundFlow-specific functions (`finance.*`) have ≥ 3 unit tests each
- Performance: PE fund metrics rule (§3.2) evaluates in < 50ms for 100-cashflow fund

## 10. Implementation notes

- **Newton-Raphson:** wrap a generic implementation in `stdlib/.../functions/financial/iterative/NewtonRaphson.java`. Reuse for `RATE`, `IRR`, `XIRR`, `MIRR`, `YIELD`, etc.
- **Sign convention:** consistently apply Excel's outflow=negative convention. Document it loudly in user-facing docs because it confuses people.
- **Day-count basis numeric vs typed:** Excel uses `0..4` integers; FundFlow's native form uses the typed `DayCount`. Provide both and a converter `finance.basis_to_day_count`.
- **Currency in TVM:** all monetary inputs to a single TVM call must share a currency. Mismatched-currency raises `FF2020`.
- **Date-handling for `XNPV`/`XIRR`:** Excel uses actual/365 implicitly. Document this and provide `finance.xnpv` / `finance.xirr` variants that take an explicit `DayCount`.
- **`MIRR` semantics:** uses two rates — financing rate for negative cashflows, reinvestment rate for positive. Standard formula: `((-NPV(reinvest, positives) * (1+reinvest)^n) / (NPV(finance, negatives) * (1+finance)))^(1/(n-1)) - 1`.
- **PE-fund metrics:** distribution and contribution conventions vary by manager. Document in user-facing docs that distributions are positive amounts (the LP receives) and contributions are also positive (the LP pays in). The MOIC etc. functions take both as positive ranges.

---

*End of Financial Functions Reference.*
