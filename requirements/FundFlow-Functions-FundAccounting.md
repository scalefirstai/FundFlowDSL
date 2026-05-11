# FundFlow Functions — Fund Accounting

**Companion to:** `FundFlow-DSL-Spec-v0.2.md`
**Namespace:** `fund.` (no Excel equivalents — these are the FundFlow domain core)
**Implementation module:** `stdlib/.../functions/fundaccounting/`

These functions encapsulate the recurring patterns of fund accounting and fund admin. They are higher-level than the financial primitives in `FundFlow-Functions-Financial.md` — they take fund domain entities (`Fund`, `ShareClass`, `Investor`, `Position`, `Transaction`) directly and return fund-meaningful results.

This is the heart of why FundFlow is a domain-specific language. Ops users should reach for these first.

## 1. NAV calculation

| Function | Signature | Notes |
|---|---|---|
| `fund.gross_assets` | `fund.gross_assets(f: Fund, as_of: D) → M` | Sum of position market values + cash |
| `fund.liabilities` | `fund.liabilities(f: Fund, as_of: D) → M` | Sum of accrued expenses + payables + borrowings |
| `fund.net_assets` | `fund.net_assets(f: Fund, as_of: D) → M` | Gross assets - liabilities |
| `fund.units_outstanding` | `fund.units_outstanding(sc: ShareClass, as_of: D) → Quantity` | |
| `fund.nav_per_unit` | `fund.nav_per_unit(sc: ShareClass, as_of: D, rounding: N?) → M` | Default 4 dp HALF_UP |
| `fund.nav` | `fund.nav(sc: ShareClass, as_of: D) → NAV` | Full NAV record |
| `fund.published_nav` | `fund.published_nav(sc: ShareClass, as_of: D) → NAV` | Last officially struck NAV (may differ from computed) |
| `fund.gav_per_unit` | `fund.gav_per_unit(sc: ShareClass, as_of: D) → M` | Gross asset value before fees |
| `fund.opening_nav` | `fund.opening_nav(sc: ShareClass, period: Pd) → M` | NAV at start of period |
| `fund.closing_nav` | `fund.closing_nav(sc: ShareClass, period: Pd) → M` | NAV at end of period |
| `fund.average_nav` | `fund.average_nav(sc: ShareClass, period: Pd, method: T?) → M` | "simple", "time_weighted", "daily" |

### 1.1 Worked example: NAV strike

```fundflow
rule "Daily NAV Strike — Class A" {
  description: "Strike NAV at end of business day"
  applies to: share class "Class A" of fund "Alpha Fund LP"

  let val_date = VALUATION_DATE()

  let gross    = fund.gross_assets(fund, val_date)
  let liab     = fund.liabilities(fund, val_date)
  let net      = gross - liab
  let units    = fund.units_outstanding(share_class, val_date)
  let nav_pu   = fund.nav_per_unit(share_class, val_date, 4)

  publish nav_pu as official nav per unit
}
```

## 2. Fee accruals

| Function | Signature | Notes |
|---|---|---|
| `fund.management_fee_accrual` | `fund.management_fee_accrual(sc: ShareClass, period: Pd) → M` | Reads fee schedule from share class |
| `fund.management_fee_for_period` | `fund.management_fee_for_period(basis: M, rate: P, period: Pd, day_count: DayCount) → M` | Explicit calculation |
| `fund.performance_fee_accrual` | `fund.performance_fee_accrual(sc: ShareClass, period: Pd) → M` | Reads schedule |
| `fund.performance_fee_calc` | `fund.performance_fee_calc(gross_return: M, basis: M, hurdle: P, perf_rate: P, hwm: M?, period: Pd, day_count: DayCount?) → M` | Explicit |
| `fund.high_water_mark` | `fund.high_water_mark(sc: ShareClass, as_of: D) → M` | Latest HWM |
| `fund.update_high_water_mark` | `fund.update_high_water_mark(sc: ShareClass, new_value: M, as_of: D) → ()` | Side effect: writes to ledger |
| `fund.crystallization_period` | `fund.crystallization_period(sc: ShareClass, as_of: D) → Pd` | Current period for perf fee |
| `fund.administrative_fee_accrual` | `fund.administrative_fee_accrual(sc: ShareClass, period: Pd) → M` | Per fee schedule |
| `fund.expense_ratio` | `fund.expense_ratio(sc: ShareClass, period: Pd) → P` | Total expenses / average NAV |

### 2.1 Performance fee semantics

`fund.performance_fee_calc` follows the standard hedge fund pattern:

1. Compute `gross_return = closing_nav - opening_nav`
2. Compute `hurdle_amount = opening_nav * hurdle * yearFraction(period, day_count)`
3. Compute `excess = MAX(0, gross_return - hurdle_amount)`
4. Compute `above_hwm = MAX(0, closing_nav - high_water_mark)` (if `hwm` provided)
5. Fee = `perf_rate * MIN(excess, above_hwm)` (or just `perf_rate * excess` if no HWM)

If `period` is partial, `yearFraction` uses the supplied day count. If `day_count` is omitted, defaults to share class day count or actual/365 if unset.

### 2.2 Worked example: full fee suite

```fundflow
rule "Quarterly Fee Crystallization" {
  description: "Mgmt fee + perf fee + admin fee crystallized at quarter end"
  applies to: all share classes of fund "Alpha Fund LP"
  effective: from 2026-01-01

  let q       = date.qtd_period(VALUATION_DATE())

  let mgmt    = fund.management_fee_accrual(share_class, q)
  let perf    = fund.performance_fee_accrual(share_class, q)
  let admin   = fund.administrative_fee_accrual(share_class, q)

  post mgmt  to ledger account "Management Fee Payable"
  post perf  to ledger account "Performance Fee Payable"
  post admin to ledger account "Administrative Fee Payable"

  when perf > USD 0 then
    fund.update_high_water_mark(share_class,
                                fund.closing_nav(share_class, q),
                                q.end)
}
```

## 3. Allocation

| Function | Signature | Notes |
|---|---|---|
| `fund.allocate_pro_rata` | `fund.allocate_pro_rata(amount: M, set: R<Investor>, weight: T) → R<(Investor, M)>` | Weight is field name |
| `fund.allocate_equally` | `fund.allocate_equally(amount: M, set: R<Investor>) → R<(Investor, M)>` | |
| `fund.allocate_by_capital_account` | `fund.allocate_by_capital_account(amount: M, fund: Fund, as_of: D) → R<(Investor, M)>` | |
| `fund.allocate_by_commitment` | `fund.allocate_by_commitment(amount: M, fund: Fund) → R<(Investor, M)>` | |
| `fund.allocate_by_unfunded` | `fund.allocate_by_unfunded(amount: M, fund: Fund, as_of: D) → R<(Investor, M)>` | Pro-rata to unfunded commitment |
| `fund.allocate_specific` | `fund.allocate_specific(amount: M, allocations: R<(Investor, P)>) → R<(Investor, M)>` | Explicit weights summing to 100% |
| `fund.rebalance_to_targets` | `fund.rebalance_to_targets(positions: R<Position>, targets: R<(T, P)>) → R<Transaction>` | Generates rebalancing trades |

### 3.1 Allocation invariants

All allocation functions guarantee:
- Sum of allocated amounts equals input amount exactly (rounding adjustment to largest allocation)
- Each allocation is non-negative if input is non-negative
- Order-independent: same inputs in different order yield same allocations (sorted by investor id internally)

### 3.2 Rounding policy

Default: "largest residual" — compute exact fractional allocations, round each to the currency's standard decimal places using HALF_UP, then adjust the largest allocation by the difference. Configurable per call:

```fundflow
fund.allocate_pro_rata(call_amount, investors, "commitment",
                       rounding = "bankers")
// Other options: "half_up" (default), "bankers", "largest_residual",
//                "stochastic" (deterministic seed from inputs)
```

## 4. Capital activity

| Function | Signature | Notes |
|---|---|---|
| `fund.capital_call` | `fund.capital_call(fund: Fund, amount: M, call_date: D, due_date: D, allocation_method: T?) → R<Transaction>` | |
| `fund.distribution` | `fund.distribution(fund: Fund, amount: M, dist_date: D, classification: T) → R<Transaction>` | classification: "income", "return_of_capital", "gain", "carry" |
| `fund.subscription` | `fund.subscription(sc: ShareClass, investor: Investor, amount: M, sub_date: D) → Transaction` | Computes units issued at NAV |
| `fund.redemption` | `fund.redemption(sc: ShareClass, investor: Investor, units: Quantity, red_date: D) → Transaction` | Computes amount at NAV |
| `fund.contributed_capital` | `fund.contributed_capital(investor: Investor, fund: Fund, as_of: D) → M` | Sum of contributions |
| `fund.distributed_capital` | `fund.distributed_capital(investor: Investor, fund: Fund, as_of: D) → M` | Sum of distributions |
| `fund.unfunded_commitment` | `fund.unfunded_commitment(investor: Investor, fund: Fund, as_of: D) → M` | Commitment - contributions + recallable distributions |
| `fund.commitment_percentage` | `fund.commitment_percentage(investor: Investor, fund: Fund) → P` | Investor commitment / total |

## 5. Performance & returns

| Function | Signature | Notes |
|---|---|---|
| `fund.gross_return` | `fund.gross_return(sc: ShareClass, period: Pd) → P` | Before fees |
| `fund.net_return` | `fund.net_return(sc: ShareClass, period: Pd) → P` | After fees |
| `fund.time_weighted_return` | `fund.time_weighted_return(sc: ShareClass, period: Pd, frequency: T?) → P` | TWR; frequency: "daily", "monthly" |
| `fund.money_weighted_return` | `fund.money_weighted_return(sc: ShareClass, period: Pd) → P` | IRR-based |
| `fund.dollar_return` | `fund.dollar_return(sc: ShareClass, period: Pd) → M` | NAV change + dist |
| `fund.return_attribution` | `fund.return_attribution(sc: ShareClass, period: Pd, factors: R<T>) → R<(T, P)>` | Brinson-style |
| `fund.fund_irr` | `fund.fund_irr(fund: Fund, as_of: D, gross: B?) → P` | Net IRR by default; XIRR underneath |
| `fund.investor_irr` | `fund.investor_irr(investor: Investor, fund: Fund, as_of: D) → P` | Per-investor IRR |
| `fund.fund_moic` | `fund.fund_moic(fund: Fund, as_of: D) → N` | MOIC at fund level |
| `fund.investor_moic` | `fund.investor_moic(investor: Investor, fund: Fund, as_of: D) → N` | |
| `fund.fund_tvpi` | `fund.fund_tvpi(fund: Fund, as_of: D) → N` | |
| `fund.fund_dpi` | `fund.fund_dpi(fund: Fund, as_of: D) → N` | |
| `fund.fund_rvpi` | `fund.fund_rvpi(fund: Fund, as_of: D) → N` | |

## 6. Waterfalls

Waterfalls are first-class declarative entities (see core spec §8) but several functional helpers are needed.

| Function | Signature | Notes |
|---|---|---|
| `fund.run_waterfall` | `fund.run_waterfall(name: T, amount: M, fund: Fund, as_of: D) → R<(T, M)>` | Returns tier-by-tier distribution |
| `fund.preferred_return_balance` | `fund.preferred_return_balance(fund: Fund, as_of: D, rate: P, day_count: DayCount) → M` | Accumulated unpaid pref |
| `fund.gp_catch_up_owed` | `fund.gp_catch_up_owed(fund: Fund, as_of: D, target_split: P) → M` | Amount needed to bring GP to target ratio |
| `fund.carry_balance` | `fund.carry_balance(fund: Fund, as_of: D) → M` | GP carry earned but not yet distributed |
| `fund.european_waterfall` | `fund.european_waterfall(amount: M, fund: Fund, as_of: D, params: WaterfallParams) → R<(T, M)>` | Whole-fund waterfall |
| `fund.american_waterfall` | `fund.american_waterfall(amount: M, deal: Deal, params: WaterfallParams) → R<(T, M)>` | Deal-by-deal |

### 6.1 Worked example: European waterfall

```fundflow
rule "European Waterfall — Distribution Event" {
  description: "Distribute $50M with 8% pref, 100% GP catch-up, 80/20 split"
  applies to: fund "Beta PE Fund III"

  let amount = USD 50,000,000

  let result = fund.european_waterfall(amount, fund, distribution_date, {
    return_of_capital_first: TRUE,
    preferred_rate: 8%,
    catch_up_rate: 100%,
    final_split: { lp: 80%, gp: 20% },
    day_count: actual/365
  })

  // result is [(tier, amount), ...] e.g.
  //   [("return_of_capital", USD 30M),
  //    ("preferred_return", USD 8M),
  //    ("gp_catch_up", USD 2M),
  //    ("split_lp", USD 8M),
  //    ("split_gp", USD 2M)]

  for (tier, tier_amount) in result do
    post tier_amount to ledger account "Distribution — " & tier
}
```

## 7. Equalization (series accounting)

For hedge funds using series accounting to handle subscriptions mid-period.

| Function | Signature | Notes |
|---|---|---|
| `fund.create_series` | `fund.create_series(sc: ShareClass, sub_date: D, amount: M) → Series` | New series at sub date |
| `fund.equalize_series` | `fund.equalize_series(sc: ShareClass, as_of: D) → R<Transaction>` | Roll series into lead series |
| `fund.equalization_credit` | `fund.equalization_credit(series: Series, as_of: D) → M` | Owed to series for not bearing past perf fee |
| `fund.equalization_debit` | `fund.equalization_debit(series: Series, as_of: D) → M` | Owed by series for accrued perf fee on entry |
| `fund.lead_series_nav` | `fund.lead_series_nav(sc: ShareClass, as_of: D) → M` | NAV per unit of original series |

## 8. Tax & wash-sale lots

| Function | Signature | Notes |
|---|---|---|
| `fund.realized_gain_loss` | `fund.realized_gain_loss(positions: R<Position>, period: Pd, method: T?) → R<(Position, M)>` | Method: "FIFO", "LIFO", "specific_lot", "average" |
| `fund.unrealized_gain_loss` | `fund.unrealized_gain_loss(positions: R<Position>, as_of: D) → M` | |
| `fund.wash_sale_adjustment` | `fund.wash_sale_adjustment(positions: R<Position>, as_of: D) → R<(Position, M)>` | US tax rule |
| `fund.short_term_gain_loss` | `fund.short_term_gain_loss(realizations: R, as_of: D) → M` | < 1 year holding |
| `fund.long_term_gain_loss` | `fund.long_term_gain_loss(realizations: R, as_of: D) → M` | ≥ 1 year holding |
| `fund.book_to_tax_difference` | `fund.book_to_tax_difference(fund: Fund, period: Pd) → M` | M-1 differences |

## 9. Reconciliation helpers

| Function | Signature | Notes |
|---|---|---|
| `fund.reconcile_cash` | `fund.reconcile_cash(fund: Fund, as_of: D, bank_balance: M) → R<Difference>` | |
| `fund.reconcile_positions` | `fund.reconcile_positions(fund: Fund, as_of: D, custodian_positions: R<Position>) → R<Difference>` | |
| `fund.trial_balance` | `fund.trial_balance(fund: Fund, as_of: D) → R<(LedgerAccount, M)>` | All ledger accounts with balances |
| `fund.balance_sheet` | `fund.balance_sheet(fund: Fund, as_of: D) → BalanceSheet` | Structured BS |
| `fund.income_statement` | `fund.income_statement(fund: Fund, period: Pd) → IncomeStatement` | Structured IS |

## 10. Investor reporting helpers

| Function | Signature | Notes |
|---|---|---|
| `fund.investor_capital_account` | `fund.investor_capital_account(investor: Investor, fund: Fund, period: Pd) → CapitalAccountStatement` | Roll-forward |
| `fund.investor_statement` | `fund.investor_statement(investor: Investor, fund: Fund, period: Pd) → InvestorStatement` | Full statement |
| `fund.k1_data` | `fund.k1_data(investor: Investor, fund: Fund, year: N) → K1Data` | US tax K-1 inputs |
| `fund.ilpa_template` | `fund.ilpa_template(fund: Fund, period: Pd) → ILPAReport` | ILPA quarterly template |

## 11. Acceptance criteria

- All functions in §1–§10 implemented in `stdlib/.../functions/fundaccounting/`
- Each function has ≥ 5 unit tests (more than other categories — these are the domain core)
- Allocation invariants tested as properties (sum equals input, non-negative, order-independent)
- Performance fee with HWM tested against documented hedge fund examples
- European and American waterfall implementations verified against three real fund LPAs (anonymized examples in `examples/waterfalls/`)
- Equalization tested with multi-series fund scenario across a full year
- Performance metrics (TWR, MWR, IRR, MOIC) cross-validated against an external reference (e.g. Burgiss methodology where licensable)
- Each function has a worked example in `docs/language-guide.md` showing typical usage
- Cumulative golden-file test: a synthetic fund's full year of operations evaluates deterministically end-to-end through subscriptions, NAV strikes, fee accruals, and a year-end distribution

## 12. Implementation notes

- **DataSource dependency:** all of these functions read from the `EvaluationContext.data` interface. Define a clean `FundDataSource` API: `getFund(id)`, `getShareClass(id)`, `getPositions(fundId, asOf)`, `getCashflows(fundId, period)`, `getNavHistory(shareClassId, period)`, etc. Mock implementations for testing live in `core/test`.
- **Immutability:** these functions are pure. Side effects (`fund.update_high_water_mark`) are NOT pure — they record an intent in the `EvaluationResult.postings`, which the platform layer applies. The DSL itself never mutates state.
- **Currency:** every Money input/output preserves currency. Multi-currency funds: each share class has a single base currency; FX is explicit via `finance.fx_convert` upstream.
- **Period sensitivity:** functions taking `period: Pd` should validate that the period falls within the fund's lifetime (not before inception, not after dissolution). Error `FF2070 period out of fund lifetime`.
- **HWM persistence:** the high-water mark is platform state. The DSL function reads it via `DataSource`. `fund.update_high_water_mark` records an intent to update; the platform applies it after the rule completes.
- **Waterfall parameters:** waterfall configuration is rich (preferred return compounding frequency, catch-up percentages, side pockets, recycling rules, GP clawback). v1 implements European and American with a documented parameter struct; advanced features (clawback, recycling) defer to v2 with explicit feature flags.
- **Equalization complexity:** real-world equalization involves dozens of edge cases (series merging on no-fee crystallization, side-pocket carve-outs, soft hurdle vs hard hurdle). Document v1 scope crisply and reject programs using out-of-scope features with a clear error.
- **K-1 and ILPA:** these are output structures, not numeric returns. Define the output schemas precisely in `core/types/reporting/`. Generation is deterministic; rendering to PDF is a platform concern.
- **Performance:** for a typical mid-size fund (200 investors, 500 positions, 12 months), a full quarterly close evaluating all relevant rules should complete in < 5 seconds. Profile and optimize the DataSource access patterns — that's where the cost lives, not in the math.

---

*End of Fund Accounting Functions Reference. End of function library companion documents.*
