# FundFlow Functions — Statistical

**Companion to:** `FundFlow-DSL-Spec-v0.2.md`
**Namespace:** `stat.` (with Excel-compatible names also in the empty namespace)
**Implementation module:** `stdlib/.../functions/statistical/`

Statistical functions for risk, performance attribution, and analytics. Implemented with `BigDecimal` adapters over Apache Commons Math 3.6 where appropriate.

## 1. Descriptive statistics

| Function | Signature | Excel | Notes |
|---|---|---|---|
| `AVERAGE` | `AVERAGE(...x: N \| R<N>) → N` | ★ | Arithmetic mean |
| `AVERAGE` (Money) | `AVERAGE(...x: M \| R<M>) → M` | ★ | Same currency required |
| `AVERAGEA` | `AVERAGEA(...x) → N` | ★ | Counts logicals as 0/1, text as 0 |
| `AVERAGEIF` | `AVERAGEIF(range: R, criterion: T, avg_range: R<N>?) → N` | ★ | |
| `AVERAGEIFS` | `AVERAGEIFS(avg_range, ...range_crit_pairs) → N` | ★ | |
| `MEDIAN` | `MEDIAN(...x: N \| R<N>) → N` | ★ | |
| `MODE.SNGL` | `MODE.SNGL(...x: N) → N` | ★ | Single most frequent |
| `MODE.MULT` | `MODE.MULT(...x: N) → R<N>` | ★ | Multiple modes |
| `GEOMEAN` | `GEOMEAN(...x: N \| R<N>) → N` | ★ | Geometric mean; all positive |
| `HARMEAN` | `HARMEAN(...x: N \| R<N>) → N` | ★ | Harmonic mean |
| `TRIMMEAN` | `TRIMMEAN(range: R<N>, percent: P) → N` | ★ | Trimmed mean |
| `COUNT` | `COUNT(...x) → N` | ★ | Counts numeric values only |
| `COUNTA` | `COUNTA(...x) → N` | ★ | Counts non-blank |
| `COUNTBLANK` | `COUNTBLANK(range: R) → N` | ★ | |
| `COUNTIF` | `COUNTIF(range: R, criterion: T) → N` | ★ | |
| `COUNTIFS` | `COUNTIFS(...range_crit_pairs) → N` | ★ | |
| `LARGE` | `LARGE(range: R<N>, k: N) → N` | ★ | k-th largest |
| `SMALL` | `SMALL(range: R<N>, k: N) → N` | ★ | k-th smallest |
| `RANK.EQ` | `RANK.EQ(x: N, range: R<N>, order: N?) → N` | ★ | Equal ranks share rank |
| `RANK.AVG` | `RANK.AVG(x: N, range: R<N>, order: N?) → N` | ★ | Average rank for ties |
| `PERCENTILE.INC` | `PERCENTILE.INC(range: R<N>, k: P) → N` | ★ | k in [0,1] |
| `PERCENTILE.EXC` | `PERCENTILE.EXC(range: R<N>, k: P) → N` | ★ | k in (0,1) |
| `QUARTILE.INC` | `QUARTILE.INC(range: R<N>, q: N) → N` | ★ | q in 0..4 |
| `QUARTILE.EXC` | `QUARTILE.EXC(range: R<N>, q: N) → N` | ★ | q in 1..3 |
| `PERCENTRANK.INC` | `PERCENTRANK.INC(range: R<N>, x: N, sig: N?) → P` | ★ | |
| `PERCENTRANK.EXC` | `PERCENTRANK.EXC(range: R<N>, x: N, sig: N?) → P` | ★ | |
| `FREQUENCY` | `FREQUENCY(data: R<N>, bins: R<N>) → R<N>` | ★ | |

## 2. Variance, deviation, correlation

| Function | Signature | Excel | Notes |
|---|---|---|---|
| `VAR.S` | `VAR.S(...x) → N` | ★ | Sample variance (n-1) |
| `VAR.P` | `VAR.P(...x) → N` | ★ | Population variance (n) |
| `VARA` | `VARA(...x) → N` | ★ | Sample, includes logicals/text |
| `VARPA` | `VARPA(...x) → N` | ★ | Population, includes logicals/text |
| `STDEV.S` | `STDEV.S(...x) → N` | ★ | Sample standard deviation |
| `STDEV.P` | `STDEV.P(...x) → N` | ★ | Population standard deviation |
| `STDEVA` | `STDEVA(...x) → N` | ★ | |
| `STDEVPA` | `STDEVPA(...x) → N` | ★ | |
| `AVEDEV` | `AVEDEV(...x) → N` | ★ | Average absolute deviation |
| `DEVSQ` | `DEVSQ(...x) → N` | ★ | Sum of squared deviations |
| `COVARIANCE.S` | `COVARIANCE.S(x: R<N>, y: R<N>) → N` | ★ | Sample |
| `COVARIANCE.P` | `COVARIANCE.P(x: R<N>, y: R<N>) → N` | ★ | Population |
| `CORREL` | `CORREL(x: R<N>, y: R<N>) → N` | ★ | Pearson correlation |
| `PEARSON` | `PEARSON(x, y) → N` | ★ | Same as CORREL |
| `RSQ` | `RSQ(known_y, known_x) → N` | ★ | r² |
| `SLOPE` | `SLOPE(known_y, known_x) → N` | ★ | OLS slope |
| `INTERCEPT` | `INTERCEPT(known_y, known_x) → N` | ★ | OLS intercept |
| `STEYX` | `STEYX(known_y, known_x) → N` | ★ | Standard error of regression |
| `FORECAST.LINEAR` | `FORECAST.LINEAR(x, known_y, known_x) → N` | ★ | |
| `TREND` | `TREND(known_y, known_x?, new_x?, const?) → R<N>` | ★ | |
| `GROWTH` | `GROWTH(known_y, known_x?, new_x?, const?) → R<N>` | ★ | Exponential trend |
| `LINEST` | `LINEST(known_y, known_x?, const?, stats?) → R<R<N>>` | ★ | Multiple regression matrix |
| `LOGEST` | `LOGEST(known_y, known_x?, const?, stats?) → R<R<N>>` | ★ | Exponential regression |
| `KURT` | `KURT(...x) → N` | ★ | Excess kurtosis |
| `SKEW` | `SKEW(...x) → N` | ★ | Skewness |
| `SKEW.P` | `SKEW.P(...x) → N` | ★ | Population skewness |

## 3. Continuous distributions

All Excel-compatible. Implemented via Apache Commons Math distributions wrapped with BigDecimal adapter.

| Function | Signature | Excel |
|---|---|---|
| `NORM.DIST` | `NORM.DIST(x: N, mean: N, sd: N, cumulative: B) → N` | ★ |
| `NORM.INV` | `NORM.INV(p: P, mean: N, sd: N) → N` | ★ |
| `NORM.S.DIST` | `NORM.S.DIST(x: N, cumulative: B) → N` | ★ |
| `NORM.S.INV` | `NORM.S.INV(p: P) → N` | ★ |
| `STANDARDIZE` | `STANDARDIZE(x: N, mean: N, sd: N) → N` | ★ |
| `LOGNORM.DIST` | `LOGNORM.DIST(x, mean, sd, cumulative) → N` | ★ |
| `LOGNORM.INV` | `LOGNORM.INV(p, mean, sd) → N` | ★ |
| `T.DIST` | `T.DIST(x, df, cumulative) → N` | ★ |
| `T.DIST.2T` | `T.DIST.2T(x, df) → N` | ★ |
| `T.DIST.RT` | `T.DIST.RT(x, df) → N` | ★ |
| `T.INV` | `T.INV(p, df) → N` | ★ |
| `T.INV.2T` | `T.INV.2T(p, df) → N` | ★ |
| `CHISQ.DIST` | `CHISQ.DIST(x, df, cumulative) → N` | ★ |
| `CHISQ.DIST.RT` | `CHISQ.DIST.RT(x, df) → N` | ★ |
| `CHISQ.INV` | `CHISQ.INV(p, df) → N` | ★ |
| `CHISQ.INV.RT` | `CHISQ.INV.RT(p, df) → N` | ★ |
| `F.DIST` | `F.DIST(x, df1, df2, cumulative) → N` | ★ |
| `F.DIST.RT` | `F.DIST.RT(x, df1, df2) → N` | ★ |
| `F.INV` | `F.INV(p, df1, df2) → N` | ★ |
| `F.INV.RT` | `F.INV.RT(p, df1, df2) → N` | ★ |
| `BETA.DIST` | `BETA.DIST(x, alpha, beta, cumulative, A?, B?) → N` | ★ |
| `BETA.INV` | `BETA.INV(p, alpha, beta, A?, B?) → N` | ★ |
| `GAMMA.DIST` | `GAMMA.DIST(x, alpha, beta, cumulative) → N` | ★ |
| `GAMMA.INV` | `GAMMA.INV(p, alpha, beta) → N` | ★ |
| `GAMMA` | `GAMMA(x) → N` | ★ |
| `GAMMALN` | `GAMMALN(x) → N` | ★ |
| `GAMMALN.PRECISE` | `GAMMALN.PRECISE(x) → N` | ★ |
| `EXPON.DIST` | `EXPON.DIST(x, lambda, cumulative) → N` | ★ |
| `WEIBULL.DIST` | `WEIBULL.DIST(x, alpha, beta, cumulative) → N` | ★ |
| `PHI` | `PHI(x: N) → N` | ★ | Standard normal PDF |
| `PROB` | `PROB(range, prob_range, lower, upper?) → N` | ★ |

## 4. Discrete distributions

| Function | Signature | Excel |
|---|---|---|
| `BINOM.DIST` | `BINOM.DIST(num_s, trials, prob, cumulative) → N` | ★ |
| `BINOM.DIST.RANGE` | `BINOM.DIST.RANGE(trials, prob, num_s, num_s2?) → N` | ★ |
| `BINOM.INV` | `BINOM.INV(trials, prob, alpha) → N` | ★ |
| `NEGBINOM.DIST` | `NEGBINOM.DIST(num_f, num_s, prob, cumulative) → N` | ★ |
| `HYPGEOM.DIST` | `HYPGEOM.DIST(sample_s, num_sample, pop_s, num_pop, cumulative) → N` | ★ |
| `POISSON.DIST` | `POISSON.DIST(x, mean, cumulative) → N` | ★ |

## 5. Confidence intervals & hypothesis tests

| Function | Signature | Excel |
|---|---|---|
| `CONFIDENCE.NORM` | `CONFIDENCE.NORM(alpha, sd, size) → N` | ★ |
| `CONFIDENCE.T` | `CONFIDENCE.T(alpha, sd, size) → N` | ★ |
| `Z.TEST` | `Z.TEST(range, x, sigma?) → N` | ★ |
| `T.TEST` | `T.TEST(array1, array2, tails, type) → N` | ★ |
| `F.TEST` | `F.TEST(array1, array2) → N` | ★ |
| `CHISQ.TEST` | `CHISQ.TEST(actual_range, expected_range) → N` | ★ |
| `FISHER` | `FISHER(x: N) → N` | ★ |
| `FISHERINV` | `FISHERINV(y: N) → N` | ★ |

## 6. FundFlow-specific risk and performance functions

These are not in Excel but are essential for fund analytics.

| Function | Signature | Notes |
|---|---|---|
| `stat.volatility` | `stat.volatility(returns: R<N>, annualization: N?) → N` | Annualized stdev of returns; default annualization 252 (daily) |
| `stat.sharpe_ratio` | `stat.sharpe_ratio(returns: R<N>, risk_free: P, annualization: N?) → N` | |
| `stat.sortino_ratio` | `stat.sortino_ratio(returns: R<N>, mar: P, annualization: N?) → N` | Minimum acceptable return |
| `stat.information_ratio` | `stat.information_ratio(returns: R<N>, benchmark: R<N>, annualization: N?) → N` | |
| `stat.tracking_error` | `stat.tracking_error(returns: R<N>, benchmark: R<N>, annualization: N?) → N` | |
| `stat.beta` | `stat.beta(returns: R<N>, benchmark: R<N>) → N` | OLS beta |
| `stat.alpha` | `stat.alpha(returns: R<N>, benchmark: R<N>, risk_free: P) → N` | Jensen's alpha |
| `stat.treynor_ratio` | `stat.treynor_ratio(returns: R<N>, benchmark: R<N>, risk_free: P) → N` | |
| `stat.max_drawdown` | `stat.max_drawdown(values: R<N>) → N` | Maximum peak-to-trough drawdown |
| `stat.drawdown_duration` | `stat.drawdown_duration(values: R<N>) → N` | Days in worst drawdown |
| `stat.calmar_ratio` | `stat.calmar_ratio(returns: R<N>, period: Pd) → N` | Annualized return / max drawdown |
| `stat.var_historical` | `stat.var_historical(returns: R<N>, confidence: P) → N` | Historical Value-at-Risk |
| `stat.var_parametric` | `stat.var_parametric(mean: N, sd: N, confidence: P) → N` | Parametric VaR |
| `stat.cvar` | `stat.cvar(returns: R<N>, confidence: P) → N` | Conditional VaR / expected shortfall |
| `stat.downside_deviation` | `stat.downside_deviation(returns: R<N>, mar: P) → N` | |
| `stat.upside_capture` | `stat.upside_capture(returns: R<N>, benchmark: R<N>) → N` | |
| `stat.downside_capture` | `stat.downside_capture(returns: R<N>, benchmark: R<N>) → N` | |
| `stat.batting_average` | `stat.batting_average(returns: R<N>, benchmark: R<N>) → N` | % periods outperforming |
| `stat.win_rate` | `stat.win_rate(returns: R<N>) → N` | % positive periods |
| `stat.gain_loss_ratio` | `stat.gain_loss_ratio(returns: R<N>) → N` | Avg gain / avg loss |

### 6.1 Worked example: hedge fund analytics

```fundflow
rule "Hedge Fund Risk Metrics" {
  description: "Standard risk-adjusted return metrics for monthly fund returns"
  applies to: fund "Alpha Hedge Fund LP"

  let returns      = fund.monthly_returns over period
  let benchmark    = benchmark.sp500_monthly_returns over period
  let rf           = market.risk_free_rate as of period.end
  let mar          = 0%  // minimum acceptable return for Sortino

  let vol          = stat.volatility(returns, 12)
  let sharpe       = stat.sharpe_ratio(returns, rf, 12)
  let sortino      = stat.sortino_ratio(returns, mar, 12)
  let beta         = stat.beta(returns, benchmark)
  let alpha        = stat.alpha(returns, benchmark, rf)
  let info_ratio   = stat.information_ratio(returns, benchmark, 12)
  let max_dd       = stat.max_drawdown(fund.nav_series over period)
  let var_95       = stat.var_historical(returns, 95%)
  let cvar_95      = stat.cvar(returns, 95%)

  publish vol, sharpe, sortino, beta, alpha, info_ratio,
          max_dd, var_95, cvar_95 as risk metrics
}
```

## 7. Time-series specific functions

These operate on `Series<D, N>` (date-indexed numeric series) — common for NAV histories, return streams, etc.

| Function | Signature | Notes |
|---|---|---|
| `stat.rolling_average` | `stat.rolling_average(s: S<D,N>, window: N) → S<D,N>` | Trailing window mean |
| `stat.rolling_volatility` | `stat.rolling_volatility(s: S<D,N>, window: N, annualization: N?) → S<D,N>` | |
| `stat.rolling_correlation` | `stat.rolling_correlation(a: S<D,N>, b: S<D,N>, window: N) → S<D,N>` | |
| `stat.rolling_beta` | `stat.rolling_beta(returns: S<D,N>, benchmark: S<D,N>, window: N) → S<D,N>` | |
| `stat.ewma` | `stat.ewma(s: S<D,N>, lambda: N) → S<D,N>` | Exponentially weighted moving average |
| `stat.cumulative_return` | `stat.cumulative_return(returns: S<D,N>) → N` | Compounded total |
| `stat.annualized_return` | `stat.annualized_return(returns: S<D,N>, frequency: N) → N` | |
| `stat.geometric_link` | `stat.geometric_link(returns: R<N>) → N` | Σ → product chain link |

## 8. Acceptance criteria

- All Excel-compat functions in §1–§5 implemented
- Excel parity test fixtures for every ★ function with inputs covering boundary cases
- FundFlow-specific functions (§6, §7) have ≥ 3 unit tests each plus property tests:
  - `stat.volatility(constant_series) = 0`
  - `stat.sharpe_ratio(returns, rf)` is invariant to scaling all returns + rf by same constant
  - `stat.max_drawdown(monotonically_increasing) = 0`
  - `stat.var_historical(returns, 100%)` = max loss (worst observation)
- Hedge fund example (§6.1) evaluates in < 100ms for 60-month return series
- Distributions match Apache Commons Math reference values to within `1e-12`

## 9. Implementation notes

- **BigDecimal for distributions:** Apache Commons Math is double-based. Wrap in a thin layer that converts BigDecimal in/out, with documented precision loss in the conversion (typically `1e-15` is fine for distribution functions). Document this in Javadoc.
- **Sample vs population:** make sure the `.S` and `.P` suffix variants use `n-1` and `n` respectively. Easy bug magnet.
- **Annualization conventions:** default to 252 (trading days) for daily returns, 12 for monthly, 52 for weekly, 4 for quarterly. Allow override.
- **Drawdown:** implemented as max over running max minus current. Test with synthetic series including ties and recoveries.
- **VaR/CVaR signs:** return as positive numbers (a 5% VaR of `0.03` means "5% chance of losing 3% or more"). Document.
- **Rolling functions:** window includes the current point unless documented otherwise. Empty windows at the start return Optional.empty in the series.
- **Geometric mean:** error if any input is non-positive; document clearly.

---

*End of Statistical Functions Reference.*
