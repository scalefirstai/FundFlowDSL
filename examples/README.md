# FundFlow Canonical Examples

Six worked examples covering the core fund-accounting computations. Each pair `<n>_<name>.ff` + `<n>_<name>.expected.json` is verified by `parser/src/test/java/.../CanonicalExamplesTest.java` — every example must parse, type-check (no fatal diagnostics), and evaluate to the documented outputs.

The `.expected.json` is a human-readable snapshot of the inputs the runtime needs and the outputs it produces. The corresponding test class supplies the same inputs as a `MapDataSource` fixture and asserts each posting / output.

| # | File | Computes | Operators exercised |
|---|---|---|---|
| 1 | `01_management_fee.ff` | Daily mgmt-fee accrual posted to liability | `accrue`, `using`, `per annum`, bare `post` |
| 2 | `02_performance_fee.ff` | Annual perf-fee against hurdle and HWM | `at start/end of`, `over … using`, `max`, `min`, `when … then` |
| 3 | `03_capital_call.ff` | Pro-rata capital call across investors | `allocate … by`, `post each allocation` |
| 4 | `04_nav_calculation.ff` | Daily NAV per unit | `sum of`, `as of`, arithmetic |
| 5 | `05_equalization.ff` | Per-series perf fee with side-pocket carve-out | per-series `let`, conditional posting |
| 6 | `06_european_waterfall.ff` | European-style PE waterfall (ROC → pref → catch-up → 80/20) | `waterfall`, `distribute`, `min` |

## 1. Management fee accrual

**Financial logic.** The fund charges 1.5% of NAV per annum, accrued daily and posted to the *Management Fee Payable* liability. Each evaluation produces one day's accrual: `nav × rate × yearFraction(asOf − 1, asOf, day_count)`. Running the rule once a day for a quarter and aggregating gives the quarterly invoice.

**Inputs.** `opening nav of share class` — Money. **Output.** One ledger posting per evaluation.

## 2. Performance fee

**Financial logic.** A standard 20-over-8 with high-water mark.

1. **Gross return** for the period: NAV at end − NAV at start.
2. **Hurdle amount**: 8% per annum on starting NAV, pro-rated for the period.
3. **Excess over hurdle** = max(0, gross_return − hurdle_amount).
4. **HWM gate** = max(0, end NAV − high-water-mark).
5. **Fee** = 20% × min(excess_over_hurdle, above_hwm). The HWM gate ensures we never charge a fee on capital that's just recovered prior losses.

**Inputs.** Period-boundary NAVs and the HWM. **Output.** Posting to *Performance Fee Payable* if fee > 0.

## 3. Capital call allocation

**Financial logic.** A capital call of N is split across investors in proportion to their unfunded commitments. Each share is `N × commitment_i / Σ commitments`. The runtime absorbs any rounding residual on the last investor so the sum of allocations exactly matches N (see the *allocation invariant* test in `AllocateTest`).

**Inputs.** A list of investor weights (`investors of fund`). **Output.** One ledger posting per investor; the `_check` field of the expected JSON verifies the sum equals the call.

> The spec example is written `allocate call_amount across investors of fund by basis`, where `basis = unfunded commitment of investor as of call date`. Today's runtime requires the `across` target to evaluate to a flat list of weights. Once the per-fund domain catalog (WP-12) lands, the same `.ff` will resolve `unfunded commitment of investor` per-investor.

## 4. NAV calculation

**Financial logic.** Standard NAV-per-unit:

```
gross_assets = Σ position market values as of valuation date
liabilities  = Σ accrued expenses + payables as of valuation date
net_assets   = gross_assets − liabilities
nav_per_unit = net_assets / units_outstanding
```

**Inputs.** Lists of position MVs and expense lines, units outstanding, plus a pre-resolved `nav as of valuation date` for the publish step (the runtime emits the published value from the data source — `net_assets / units` is computed but not currently published).

## 5. Series equalization

**Financial logic.** Multi-series hedge funds run a separate high-water mark per subscription series so each investor's fee is computed against the NAV at *their* subscription date.

For each series:
- Subtract any side-pocket carve-out from the current NAV (side-pocket assets accrue their own performance separately).
- Compute appreciation = max(0, performance NAV − HWM).
- Performance fee = 20% × appreciation.
- Post only when fee > 0 (the `when … then` guard skips series that haven't recovered to HWM).

In the example fixture: Series A's perf-NAV exceeds HWM by USD 1M → 200k fee; Series B is below HWM → no posting.

## 6. European waterfall

**Financial logic.** Classic European-style limited-partner / general-partner distribution. All proceeds flow through four tiers in order, each consuming what's left:

1. **Return of Capital (LP)** — pay LP back the drawn capital first.
2. **Preferred Return (LP)** — 8% on drawn capital.
3. **GP Catch-up** — pay GP until they hold 20% of (pref + catch-up). Solving algebraically, `catch_up = pref × 0.25` gives a 100% catch-up to a 20% target.
4. **80/20 Split** — remaining proceeds split LP 80% / GP 20%.

The example walks USD 200M of proceeds against USD 100M drawn capital:

| Tier | Amount | Recipient |
|---|---|---|
| Return of Capital | 100,000,000 | LP |
| Preferred Return  | 8,000,000   | LP |
| GP Catch-up       | 2,000,000   | GP |
| 80% of remainder  | 72,000,000  | LP |
| 20% of remainder  | 18,000,000  | GP |
| **Total**         | **200,000,000** | sum of tier postings |

LP receives 100M + 8M + 72M = **180M** (90%); GP receives 2M + 18M = **20M** (10%, which is 20% of profit-after-pref).

## Running the examples

```
mvn -pl parser test -Dtest=CanonicalExamplesTest
```

Each test method names the example and prints the failing assertion if the runtime output diverges from the expected golden file. Update both the `.ff` and the `.expected.json` together when you change a rule's behaviour.
