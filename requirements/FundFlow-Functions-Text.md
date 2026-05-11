# FundFlow Functions — Text

**Companion to:** `FundFlow-DSL-Spec-v0.2.md`
**Namespace:** `text.` (with Excel-compatible names also in the empty namespace)
**Implementation module:** `stdlib/.../functions/text/`

Text functions for narrative generation (ledger entry narratives, report labels, investor communications) and string manipulation. Less central to fund accounting than financial or date functions, but ops users expect Excel parity here.

## 1. Construction & extraction

| Function | Signature | Excel | Notes |
|---|---|---|---|
| `CONCAT` | `CONCAT(...t: T \| R<T>) → T` | ★ | Variadic concatenation |
| `CONCATENATE` | `CONCATENATE(...t: T) → T` | ★ | Legacy alias of CONCAT |
| `TEXTJOIN` | `TEXTJOIN(delimiter: T, ignore_empty: B, ...t: T \| R<T>) → T` | ★ | |
| `LEFT` | `LEFT(t: T, n: N?) → T` | ★ | Default n=1 |
| `RIGHT` | `RIGHT(t: T, n: N?) → T` | ★ | |
| `MID` | `MID(t: T, start: N, n: N) → T` | ★ | 1-indexed |
| `LEN` | `LEN(t: T) → N` | ★ | Code-point count, not byte count |
| `LENB` | `LENB(t: T) → N` | ★ | UTF-8 byte count |
| `REPT` | `REPT(t: T, n: N) → T` | ★ | Repeat |
| `TEXTBEFORE` | `TEXTBEFORE(t: T, delim: T, instance: N?, match_mode: N?, match_end: N?, if_not_found?: T) → T` | ★ | |
| `TEXTAFTER` | `TEXTAFTER(t: T, delim: T, instance: N?, match_mode: N?, match_end: N?, if_not_found?: T) → T` | ★ | |
| `TEXTSPLIT` | `TEXTSPLIT(t: T, col_delim: T?, row_delim: T?, ignore_empty: B?, match_mode: N?, pad?: T) → R` | ★ | |

## 2. Search and replace

| Function | Signature | Excel | Notes |
|---|---|---|---|
| `FIND` | `FIND(needle: T, hay: T, start: N?) → N` | ★ | Case-sensitive; error if not found |
| `FINDB` | `FINDB(needle, hay, start?) → N` | ★ | Byte-based |
| `SEARCH` | `SEARCH(needle: T, hay: T, start: N?) → N` | ★ | Case-insensitive; supports `*` and `?` wildcards |
| `SEARCHB` | `SEARCHB(needle, hay, start?) → N` | ★ | |
| `SUBSTITUTE` | `SUBSTITUTE(t: T, old: T, new: T, instance: N?) → T` | ★ | |
| `REPLACE` | `REPLACE(t: T, start: N, n: N, new: T) → T` | ★ | Position-based |
| `REPLACEB` | `REPLACEB(t, start, n, new) → T` | ★ | |
| `text.regex_match` | `text.regex_match(t: T, pattern: T) → B` | | Java regex; sandboxed (timeout) |
| `text.regex_extract` | `text.regex_extract(t: T, pattern: T, group: N?) → T` | | |
| `text.regex_replace` | `text.regex_replace(t: T, pattern: T, replacement: T) → T` | | |

## 3. Case and trimming

| Function | Signature | Excel |
|---|---|---|
| `UPPER` | `UPPER(t: T) → T` | ★ |
| `LOWER` | `LOWER(t: T) → T` | ★ |
| `PROPER` | `PROPER(t: T) → T` | ★ Title case |
| `TRIM` | `TRIM(t: T) → T` | ★ |
| `CLEAN` | `CLEAN(t: T) → T` | ★ Strip non-printable |
| `text.trim_start` | `text.trim_start(t: T) → T` | |
| `text.trim_end` | `text.trim_end(t: T) → T` | |

## 4. Comparison

| Function | Signature | Excel |
|---|---|---|
| `EXACT` | `EXACT(a: T, b: T) → B` | ★ Case-sensitive equality |
| `text.equals_ignore_case` | `text.equals_ignore_case(a: T, b: T) → B` | |
| `text.starts_with` | `text.starts_with(t: T, prefix: T) → B` | |
| `text.ends_with` | `text.ends_with(t: T, suffix: T) → B` | |
| `text.contains` | `text.contains(t: T, substr: T) → B` | |

## 5. Conversion to/from text

| Function | Signature | Excel | Notes |
|---|---|---|---|
| `TEXT` | `TEXT(value, format: T) → T` | ★ | Excel format code (e.g. "#,##0.00") |
| `VALUE` | `VALUE(t: T) → N` | ★ | Parse numeric |
| `NUMBERVALUE` | `NUMBERVALUE(t: T, decimal: T?, thousands: T?) → N` | ★ | Locale-aware |
| `FIXED` | `FIXED(x: N, decimals: N?, no_commas: B?) → T` | ★ | |
| `DOLLAR` | `DOLLAR(x: N, decimals: N?) → T` | ★ | Formats with currency symbol |
| `BAHTTEXT` | not implemented | | Thai-only, niche |
| `text.format_money` | `text.format_money(m: M, format: T?, locale: T?) → T` | | Locale-aware money formatting |
| `text.format_date` | `text.format_date(d: D, format: T?, locale: T?) → T` | | |
| `text.format_percent` | `text.format_percent(p: P, decimals: N?) → T` | | |
| `text.parse_money` | `text.parse_money(t: T, currency: T?) → M` | | Reverse of format |
| `text.parse_date` | `text.parse_date(t: T, format: T?) → D` | | |

### 5.1 Excel format codes

`TEXT` accepts standard Excel format codes:
- `0` and `#` for digits (0 pads, # doesn't)
- `,` for thousands separator
- `.` for decimal
- `%` for percentage
- `e+0` or `E+0` for scientific
- Date codes: `yyyy`, `mm`, `dd`, `mmm`, `mmmm`, `ddd`, `dddd`, `hh`, `mm` (minutes — context-disambiguated), `ss`, `AM/PM`
- `[Red]`, `[Blue]` color codes — accepted but stripped (no color in DSL output)
- `;` for positive;negative;zero;text sections

Document the supported subset clearly. Excel format codes have many edge cases.

## 6. Character codes

| Function | Signature | Excel |
|---|---|---|
| `CHAR` | `CHAR(n: N) → T` | ★ |
| `UNICHAR` | `UNICHAR(n: N) → T` | ★ |
| `CODE` | `CODE(t: T) → N` | ★ Code point of first char |
| `UNICODE` | `UNICODE(t: T) → N` | ★ |

## 7. FundFlow-specific text functions

| Function | Signature | Notes |
|---|---|---|
| `text.fund_label` | `text.fund_label(f: Fund) → T` | Standard fund display name |
| `text.share_class_label` | `text.share_class_label(sc: ShareClass) → T` | |
| `text.investor_label` | `text.investor_label(inv: Investor) → T` | Respects PII flags |
| `text.period_label` | `text.period_label(p: Pd) → T` | "Q1 2026", "March 2026", "Jan 1 – Mar 31, 2026" |
| `text.narrative` | `text.narrative(template: T, ...kv: (T, Any)) → T` | Named-parameter templating; safer than CONCAT |

### 7.1 Worked example: ledger entry narrative

```fundflow
rule "Management Fee Posting" {
  // ... fee calculation above ...

  let period_label = text.period_label(accrual_period)
  let class_label  = text.share_class_label(share_class)
  let narrative = text.narrative(
    "Mgmt fee accrual for {class} for {period}: {amount}",
    "class", class_label,
    "period", period_label,
    "amount", text.format_money(fee, "#,##0.00")
  )

  post fee to ledger account "Management Fee Payable" with narrative narrative
}
```

## 8. Acceptance criteria

- All Excel-compat functions in §1–§6 implemented with parity tests
- Wildcard support in `SEARCH` / `SEARCHB` (`*`, `?`, `~` escape) covered by tests
- `text.regex_*` functions sandboxed with 100ms timeout to prevent ReDoS
- Format strings tested across common cases: positive/negative/zero numbers, fractional precision, dates, percentages
- Locale-aware functions tested for at least: en-US, en-GB, de-DE, ja-JP, fr-FR
- Round-trip property: `VALUE(TEXT(x, "0.00"))` ≈ `ROUND(x, 2)` within format precision

## 9. Implementation notes

- **Unicode correctness:** `LEN` counts code points, not UTF-16 code units. Java strings are UTF-16; use `String.codePointCount`. `MID` and `LEFT`/`RIGHT` likewise must be code-point aware to avoid splitting surrogate pairs.
- **`SEARCH` wildcards:** convert `*` → `.*`, `?` → `.`, `~*` → `\*`, `~?` → `\?` for the regex internally. Don't expose regex to the user via this function — only via `text.regex_*`.
- **Regex sandboxing:** wrap `Pattern.compile` and matching with a timeout-bound executor. ReDoS attacks are real; limit execution time per call.
- **Format strings:** parse Excel format strings into a structured `FormatSpec` AST, evaluate against the value. Reuse for `TEXT`, `FIXED`, `DOLLAR`. Don't try to use Java's `DecimalFormat` directly — semantics differ in ways that bite.
- **Date format ambiguity:** `m` in Excel format means month before time codes, minutes after `h`. Detect context.
- **Money formatting:** `text.format_money` should use the currency's standard decimal places (USD=2, JPY=0, BHD=3) by default. Allow override via the format parameter.
- **PII handling:** `text.investor_label` should check for any "redact" flags on the investor record and substitute `[redacted]` if set. This is platform-policy driven, not a DSL concern, but the function provides the hook.

---

*End of Text Functions Reference.*
