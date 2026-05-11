# Test Dataset Reference
Generated dataset for the Mutual Fund Distribution Engine. The workbook ships with this exact data already populated; these files are provided so you can validate, diff, or re-import.
## Files
| File | Purpose |
|---|---|
| `test_dataset.xlsx` | All inputs (Funds, Periods, Investors, Tax_Rates, Holdings) on separate tabs |
| `expected_outputs.xlsx` | What `Distributions` and `Summary` sheets should contain after macros run |
| `01_funds.csv` ... `05_holdings.csv` | Individual CSVs of each input table |

## Headline expected totals
After clicking **Run Distribution Calculation** on the workbook:
| Metric | Value |
|---|--:|
| Distribution events generated | **160 rows** |
| Grand total Gross Distribution | **$216,929.20** |
| Grand total Tax Withheld | **$25,367.55** |
| Grand total Net Distribution | **$191,561.65** |
| Total Reinvested Units | **8,187.2142** |

After **Generate Summary Report** you should see **12 rollup rows** (3 funds × 4 quarters).

## Fund × Period rollup (expected `Summary` sheet)
| Fund | Period | Total Units | DPU | Gross | Tax | Net | Cash | Reinv |
|---|---|--:|--:|--:|--:|--:|--:|--:|
| BALF | Q1 2025 | 140,766.46 | $0.112000 | $15,765.84 | $1,759.35 | $14,006.49 | $6,930.56 | $7,075.93 |
| BALF | Q2 2025 | 144,348.47 | $0.123100 | $17,765.97 | $1,980.73 | $15,785.23 | $7,891.68 | $7,893.56 |
| BALF | Q3 2025 | 144,181.16 | $0.112700 | $16,243.59 | $1,805.51 | $14,438.08 | $7,265.92 | $7,172.16 |
| BALF | Q4 2025 | 145,041.32 | $0.126700 | $18,376.96 | $2,037.41 | $16,339.55 | $8,168.52 | $8,171.03 |
| EQGF | Q1 2025 | 106,737.39 | $0.116000 | $12,381.54 | $1,632.79 | $10,748.74 | $7,868.53 | $2,880.21 |
| EQGF | Q2 2025 | 107,895.64 | $0.123700 | $13,342.82 | $1,756.64 | $11,586.18 | $8,538.69 | $3,047.49 |
| EQGF | Q3 2025 | 109,583.88 | $0.102600 | $11,243.55 | $1,479.77 | $9,763.78 | $7,232.96 | $2,530.82 |
| EQGF | Q4 2025 | 110,989.65 | $0.125900 | $13,973.52 | $1,836.03 | $12,137.49 | $9,002.16 | $3,135.33 |
| INCF | Q1 2025 | 197,811.39 | $0.116100 | $22,971.65 | $2,604.56 | $20,367.08 | $10,233.32 | $10,133.77 |
| INCF | Q2 2025 | 202,795.85 | $0.120900 | $24,524.15 | $2,773.39 | $21,750.76 | $11,045.58 | $10,705.18 |
| INCF | Q3 2025 | 204,669.75 | $0.121300 | $24,817.74 | $2,813.16 | $22,004.58 | $11,233.66 | $10,770.92 |
| INCF | Q4 2025 | 207,177.63 | $0.123200 | $25,521.88 | $2,888.21 | $22,633.67 | $11,523.27 | $11,110.41 |

## Spot-check rows (Q1 2025)
Pick these from the `Distributions` sheet to validate per-tax-class behavior:

| Scenario | Investor | Fund | Units | DPU | Gross | Rate | Tax | Net | Reinv Units |
|---|---|---|--:|--:|--:|--:|--:|--:|--:|
| Resident/Cash | INV001 | EQGF | 10,312.5484 | $0.116000 | $1,196.26 | 10.00% | $119.63 | $1,076.63 | 0.0000 |
| Resident/Cash | INV001 | INCF | 17,520.0106 | $0.116129 | $2,034.58 | 10.00% | $203.46 | $1,831.12 | 0.0000 |
| NRI/Cash | INV004 | EQGF | 4,865.7281 | $0.116000 | $564.42 | 20.00% | $112.88 | $451.54 | 0.0000 |
| NRI/Cash | INV004 | BALF | 15,087.2888 | $0.112000 | $1,689.78 | 20.00% | $337.96 | $1,351.82 | 0.0000 |
| Senior/Cash | INV006 | BALF | 15,712.4155 | $0.112000 | $1,759.79 | 7.50% | $131.98 | $1,627.81 | 0.0000 |
| Senior/Cash | INV006 | INCF | 14,115.8776 | $0.116129 | $1,639.26 | 7.50% | $122.94 | $1,516.32 | 0.0000 |
| Trust/Reinvest | INV015 | EQGF | 3,181.8224 | $0.116000 | $369.09 | 10.00% | $36.91 | $332.18 | 31.7878 |
| Trust/Reinvest | INV015 | BALF | 14,881.1905 | $0.112000 | $1,666.69 | 10.00% | $166.67 | $1,500.02 | 146.9171 |
| Trust/Reinvest | INV015 | INCF | 21,934.0635 | $0.116129 | $2,547.18 | 10.00% | $254.72 | $2,292.46 | 226.5280 |
| Trust Exempt/Cash | INV016 | BALF | 3,172.2153 | $0.112000 | $355.29 | 0.00% | $0.00 | $355.29 | 0.0000 |
| Foreign Co/Cash | INV024 | EQGF | 7,500.7932 | $0.116000 | $870.09 | 20.00% | $174.02 | $696.07 | 0.0000 |

## Distribution-per-unit (DPU) reference
Computed by the workbook formula `= DistributableIncome / TotalUnitsOutstanding`:

| Fund | Q1 2025 | Q2 2025 | Q3 2025 | Q4 2025 |
|---|--:|--:|--:|--:|
| EQGF | $0.116000 | $0.123664 | $0.102602 | $0.125899 |
| BALF | $0.112000 | $0.123077 | $0.112661 | $0.126702 |
| INCF | $0.116129 | $0.120930 | $0.121257 | $0.123188 |

## Calculation rules (recap)
For each row in `Distributions`:
```
dist_per_unit  = distributable_income / units_outstanding   (per fund/period)
gross          = units_held_at_period_end * dist_per_unit
tax_rate       = tax_rates[investor.tax_class][fund.strategy]
tax            = gross * tax_rate
net            = gross - tax
reinvested_units = (net / nav)  if pref = 'Reinvest' and nav > 0 else 0
```
