# FundFlow Functions — Date / Time / Period

**Companion to:** `FundFlow-DSL-Spec-v0.2.md`
**Namespace:** `date.` (with Excel-compatible names also in the empty namespace)
**Implementation module:** `stdlib/.../functions/datetime/`

Date math is central to fund accounting. FundFlow uses `LocalDate`, `BusinessDate`, and `Period` as native types; this module provides functions for constructing, deconstructing, and manipulating them.

**No volatile functions:** `NOW()` and `TODAY()` from Excel are replaced by `AS_OF_DATE()` and `VALUATION_DATE()` which are bound to `EvaluationContext.asOf` and therefore deterministic.

## 1. Construction & deconstruction

| Function | Signature | Excel | Notes |
|---|---|---|---|
| `DATE` | `DATE(year: N, month: N, day: N) → D` | ★ | |
| `TIME` | `TIME(hour, minute, second) → DT` | ★ | DateTime, not Date |
| `DATEVALUE` | `DATEVALUE(text: T) → D` | ★ | Parses ISO and common formats |
| `TIMEVALUE` | `TIMEVALUE(text: T) → DT` | ★ | |
| `YEAR` | `YEAR(d: D) → N` | ★ | |
| `MONTH` | `MONTH(d: D) → N` | ★ | 1–12 |
| `DAY` | `DAY(d: D) → N` | ★ | |
| `HOUR` / `MINUTE` / `SECOND` | DateTime accessors | ★ | |
| `WEEKDAY` | `WEEKDAY(d: D, type: N?) → N` | ★ | type 1=Sun-1, 2=Mon-1, 3=Mon-0 |
| `WEEKNUM` | `WEEKNUM(d: D, type: N?) → N` | ★ | |
| `ISOWEEKNUM` | `ISOWEEKNUM(d: D) → N` | ★ | ISO 8601 week |
| `DAYS` | `DAYS(end: D, start: D) → N` | ★ | Calendar days |
| `DAYS360` | see Financial doc | ★ | |
| `DATEDIF` | `DATEDIF(start, end, unit: T) → N` | ★ | Y, M, D, MD, YM, YD |
| `EDATE` | `EDATE(start, months) → D` | ★ | |
| `EOMONTH` | `EOMONTH(start, months) → D` | ★ | End of month |

## 2. Context-bound deterministic functions (replace Excel volatile)

| Function | Signature | Replaces | Notes |
|---|---|---|---|
| `AS_OF_DATE` | `AS_OF_DATE() → D` | `TODAY()` | Returns `EvaluationContext.asOf.date()` |
| `AS_OF_BUSINESS_DATE` | `AS_OF_BUSINESS_DATE() → BD` | | Same with calendar attached |
| `VALUATION_DATE` | `VALUATION_DATE() → D` | | Alias for `AS_OF_DATE` (semantic clarity) |
| `TRADE_DATE` | `TRADE_DATE() → D` | | Trade-date in current evaluation |
| `SETTLE_DATE` | `SETTLE_DATE(trade: D, days: N, calendar: T?) → D` | | T+n settlement |
| `RUNTIME_DATE` | not implemented | `NOW()` | Wall-clock not exposed to programs |

## 3. Business-day arithmetic

| Function | Signature | Excel | Notes |
|---|---|---|---|
| `WORKDAY` | `WORKDAY(start: D, days: N, holidays: R<D>?) → D` | ★ | Excludes weekends + holidays |
| `WORKDAY.INTL` | `WORKDAY.INTL(start, days, weekend?, holidays?) → D` | ★ | Custom weekend pattern |
| `NETWORKDAYS` | `NETWORKDAYS(start, end, holidays?) → N` | ★ | |
| `NETWORKDAYS.INTL` | `NETWORKDAYS.INTL(start, end, weekend?, holidays?) → N` | ★ | |
| `date.is_business_day` | `date.is_business_day(d: D, calendar: T?) → B` | | Defaults to context calendar |
| `date.next_business_day` | `date.next_business_day(d: D, calendar: T?) → D` | | |
| `date.prev_business_day` | `date.prev_business_day(d: D, calendar: T?) → D` | | |
| `date.add_business_days` | `date.add_business_days(d: D, n: N, calendar: T?) → D` | | Negative n goes backward |
| `date.business_days_between` | `date.business_days_between(start: D, end: D, calendar: T?) → N` | | |
| `date.last_business_day_of_month` | `date.last_business_day_of_month(d: D, calendar: T?) → D` | | |
| `date.last_business_day_of_quarter` | `date.last_business_day_of_quarter(d: D, calendar: T?) → D` | | |
| `date.last_business_day_of_year` | `date.last_business_day_of_year(d: D, calendar: T?) → D` | | |
| `date.first_business_day_of_month` | similar | | |
| `date.holidays` | `date.holidays(year: N, calendar: T?) → R<D>` | | Returns the calendar's holiday list |

### 3.1 Calendar resolution

If `calendar` is omitted, the context default is used (`EvaluationContext.defaultCalendar`). Named calendars include at minimum: `NYSE`, `NASDAQ`, `LSE`, `TARGET2`, `JPX`, `HKEX`, `SIX`, `TSX`, plus customer-defined calendars loaded via `DataSource`.

## 4. Period manipulation

| Function | Signature | Notes |
|---|---|---|
| `date.period_start` | `date.period_start(p: Pd) → D` | |
| `date.period_end` | `date.period_end(p: Pd) → D` | |
| `date.period_days` | `date.period_days(p: Pd) → N` | Calendar days |
| `date.period_business_days` | `date.period_business_days(p: Pd, calendar: T?) → N` | |
| `date.period_intersect` | `date.period_intersect(a: Pd, b: Pd) → Pd?` | Optional |
| `date.period_union` | `date.period_union(a: Pd, b: Pd) → Pd` | Error if non-contiguous |
| `date.period_contains` | `date.period_contains(p: Pd, d: D) → B` | |
| `date.period_overlap_days` | `date.period_overlap_days(a: Pd, b: Pd) → N` | 0 if disjoint |
| `date.period_split_by_month` | `date.period_split_by_month(p: Pd) → R<Pd>` | |
| `date.period_split_by_quarter` | `date.period_split_by_quarter(p: Pd) → R<Pd>` | |
| `date.period_split_by_year` | `date.period_split_by_year(p: Pd) → R<Pd>` | |
| `date.ytd_period` | `date.ytd_period(d: D) → Pd` | Year-to-date period ending d |
| `date.mtd_period` | `date.mtd_period(d: D) → Pd` | Month-to-date period ending d |
| `date.qtd_period` | `date.qtd_period(d: D) → Pd` | Quarter-to-date period ending d |
| `date.itd_period` | `date.itd_period(inception: D, d: D) → Pd` | Inception-to-date |
| `date.previous_period` | `date.previous_period(p: Pd) → Pd` | Same length, immediately prior |
| `date.next_period` | `date.next_period(p: Pd) → Pd` | Same length, immediately after |

## 5. Quarter, month, week helpers

| Function | Signature | Notes |
|---|---|---|
| `date.quarter` | `date.quarter(d: D) → N` | 1–4 |
| `date.quarter_start` | `date.quarter_start(d: D) → D` | |
| `date.quarter_end` | `date.quarter_end(d: D) → D` | |
| `date.month_start` | `date.month_start(d: D) → D` | |
| `date.month_end` | `date.month_end(d: D) → D` | |
| `date.year_start` | `date.year_start(d: D) → D` | |
| `date.year_end` | `date.year_end(d: D) → D` | |
| `date.is_month_end` | `date.is_month_end(d: D) → B` | |
| `date.is_quarter_end` | `date.is_quarter_end(d: D) → B` | |
| `date.is_year_end` | `date.is_year_end(d: D) → B` | |
| `date.weeks_between` | `date.weeks_between(a: D, b: D) → N` | |
| `date.months_between` | `date.months_between(a: D, b: D) → N` | Whole months |
| `date.years_between` | `date.years_between(a: D, b: D) → N` | Whole years |

## 6. Day-count fractions

Listed primarily in Financial doc (§6 there) but mirrored here for discoverability.

| Function | Signature | Excel | Notes |
|---|---|---|---|
| `YEARFRAC` | `YEARFRAC(start: D, end: D, basis: N?) → N` | ★ | Excel basis 0..4 |
| `date.year_fraction` | `date.year_fraction(start: D, end: D, day_count: DayCount) → N` | | Native typed form |
| `date.day_count_from_basis` | `date.day_count_from_basis(basis: N) → DayCount` | | Convert Excel basis to typed DayCount |

## 7. Fiscal calendar support

Many funds use fiscal years that don't align with calendar years.

| Function | Signature | Notes |
|---|---|---|
| `date.fiscal_year` | `date.fiscal_year(d: D, fy_start_month: N) → N` | Returns fiscal year number |
| `date.fiscal_quarter` | `date.fiscal_quarter(d: D, fy_start_month: N) → N` | 1–4 |
| `date.fiscal_year_start` | `date.fiscal_year_start(year: N, fy_start_month: N) → D` | |
| `date.fiscal_year_end` | `date.fiscal_year_end(year: N, fy_start_month: N) → D` | |
| `date.fiscal_period` | `date.fiscal_period(year: N, fy_start_month: N) → Pd` | Whole fiscal year as Period |

## 8. Worked examples

```fundflow
// Standard month-end NAV with T+1 strike date
let valuation_date = date.last_business_day_of_month(VALUATION_DATE())
let strike_date    = date.next_business_day(valuation_date)

// Days remaining in current quarter for fee proration
let remaining_days = date.period_days(
    from VALUATION_DATE() to date.quarter_end(VALUATION_DATE()))

// QTD return calculation
let qtd_period   = date.qtd_period(VALUATION_DATE())
let qtd_returns  = fund.daily_returns over qtd_period
let qtd_total    = stat.geometric_link(qtd_returns)

// Bond settlement T+2 on NYSE
let trade_date     = 2026-04-30
let settle_date    = date.add_business_days(trade_date, 2, "NYSE")

// Split a multi-month accrual into monthly buckets
let accrual_period = from 2026-01-15 to 2026-04-15
let monthly_chunks = date.period_split_by_month(accrual_period)
// Use in a per-period accrual rule
```

## 9. Acceptance criteria

- All Excel-compat functions in §1–§3, §6 implemented with parity tests
- Calendar-aware functions tested against published holiday schedules for NYSE, LSE, TARGET2 covering 2020–2030
- Period operations have property tests:
  - `period_intersect(a, a) = a`
  - `period_intersect(a, b) = period_intersect(b, a)`
  - `period_days(period_intersect(a, b)) ≤ min(period_days(a), period_days(b))`
  - `period_overlap_days(disjoint_periods) = 0`
- Fiscal-calendar functions tested with several common fiscal year starts (Jan, Apr, Jul, Oct)
- All functions handle leap years correctly; explicit tests for 2024, 2028
- Performance: any single call returns in < 5ms

## 10. Implementation notes

- **Use `java.time` underneath:** `LocalDate`, `LocalDateTime`, `Year`, `YearMonth` from `java.time` are immutable, thread-safe, and battle-tested. Wrap them; don't reinvent.
- **`BusinessCalendar` interface:** simple — `boolean isHoliday(LocalDate)` and `boolean isWeekend(LocalDate)`. Implementations load holiday lists from JSON resources or `DataSource`.
- **Excel date serial numbers:** Excel stores dates as numbers (1900-01-01 = 1, with the famous Lotus 1-2-3 leap year bug). FundFlow does NOT expose serial numbers. Excel-import handles the conversion.
- **`DATEDIF` quirks:** Excel's `DATEDIF` has known bugs in some unit combinations. Document FundFlow's behavior explicitly per unit; match Excel where unambiguous, document deviations otherwise.
- **`WEEKDAY` types:** the `type` parameter is fiddly. Test all 11 valid values (1, 2, 3, 11..17).
- **`Period` is closed-closed:** `from 2026-01-01 to 2026-01-31` includes both endpoints. Document loudly because some systems use half-open intervals.
- **Fiscal year naming:** the fiscal year covering Apr 2026 – Mar 2027 is "FY2027" by US convention but "FY2026/27" by some others. Default to "year of the end date"; allow override.

---

*End of Date / Time / Period Functions Reference.*
