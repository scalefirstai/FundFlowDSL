# FundFlow Core Types — Cheatsheet (WP-1)

All types live in `ai.getfundflow.dsl.core.types` (with calendars in `ai.getfundflow.dsl.core.calendar`). Every type is an immutable Java record or sealed interface; equality and hashing are value-based. `BigDecimal` is the only numeric type used; `MathContext.DECIMAL64` is the default for intermediate calculations.

---

## Money

```java
public record Money(BigDecimal amount, Currency currency)
```

The `amount` is normalized to the currency's default fraction digits on construction (HALF_EVEN). JPY is therefore stored at scale 0; USD/EUR at scale 2.

```java
Money fee  = Money.of("1000000", "USD").multiply(Percentage.ofPercent("2"));   // USD 20000.00
Money jpy  = Money.of("1000.49", "JPY");                                       // JPY 1000
Money sum  = Money.of("100", "USD").plus(Money.of("250.50", "USD"));           // USD 350.50
Money flip = Money.of("100", "USD").negate();                                  // USD -100.00
Money eur  = Money.of("100", "USD").convert(usdToEur);                         // EUR ...
```

Mismatched-currency arithmetic throws `CurrencyMismatchException`.

## FxRate

```java
public record FxRate(Currency from, Currency to, BigDecimal rate, Instant asOf)
```

```java
FxRate r = new FxRate(USD, EUR, new BigDecimal("0.92"), Instant.parse("2026-01-01T00:00:00Z"));
Money eur = r.apply(Money.of("100", "USD"));    // EUR 92.00
FxRate inv = r.inverse(Instant.now());          // EUR -> USD at 1/0.92
```

## BusinessDate

```java
public record BusinessDate(LocalDate date, BusinessCalendar calendar)
```

```java
BusinessDate friday = new BusinessDate(LocalDate.of(2026, 3, 27), WeekendOnlyCalendar.DEFAULT);
friday.isBusinessDay();                  // true
friday.plusBusinessDays(1).date();       // 2026-03-30 (Monday — weekend skipped)
friday.previousBusinessDay().date();     // 2026-03-26
```

`BusinessCalendar` is a sealed interface; v1 ships `WeekendOnlyCalendar`. Real exchange calendars (NYSE, LSE, TSE) land later.

## Period

```java
public sealed interface Period permits CalendarPeriod, NamedPeriod, RelativePeriod
```

| Variant | Example |
|---|---|
| `CalendarPeriod` | `CalendarPeriod.ofQuarter(2026, 1)`, `CalendarPeriod.ofMonth(2026, MARCH)`, `CalendarPeriod.ofYear(2026)`, `new CalendarPeriod(start, end)` |
| `NamedPeriod` | `NamedPeriod.ytd(asOf)`, `NamedPeriod.mtd(asOf)`, `NamedPeriod.qtd(asOf)`, `NamedPeriod.sinceInception(asOf, inception)` |
| `RelativePeriod` | `RelativePeriod.trailing(anchor, 30)`, `RelativePeriod.leading(anchor, 7)` |

Common operations:

```java
period.start();                               // LocalDate
period.end();                                 // LocalDate
period.days();                                // calendar days, inclusive
period.businessDays(WeekendOnlyCalendar.DEFAULT);
period.contains(LocalDate.of(2026, 3, 15));
period.intersect(otherPeriod);                // Optional<Period>
```

`p.intersect(p)` is idempotent (returns `Optional<Period>` with the same bounds).

## Percentage

```java
public record Percentage(BigDecimal value)   // value = ratio, e.g., 1.5% -> 0.015
```

```java
Percentage mgmtFee = Percentage.ofPercent("1.5");   // 0.015
Percentage spread  = Percentage.ofBps(25);          // 0.0025
mgmtFee.applyTo(Money.of("1000000", "USD"));        // USD 15000.00
mgmtFee.asPercent();                                // 1.5
mgmtFee.asBps();                                    // 150
```

`Percentage` is distinct from raw `BigDecimal` so the type system can enforce that `Money * Percentage` is a fee/return, not an FX rate.

## DayCount

```java
public sealed interface DayCount permits Actual360, Actual365, Thirty360, ActualActual
```

```java
Actual360.INSTANCE.yearFraction(start, end);     // (end - start) / 360
Actual365.INSTANCE.yearFraction(start, end);     // (end - start) / 365
Thirty360.INSTANCE.yearFraction(start, end);     // 30/360 US, with end-of-month adjustment
ActualActual.INSTANCE.yearFraction(start, end);  // ISDA, splits across year boundaries by leap status
```

`code()` returns the literal-syntax form: `"actual/360"`, `"actual/365"`, `"30/360"`, `"actual/actual"`.

## Quantity / Unit

```java
public record Quantity(BigDecimal value, Unit unit)
public sealed interface Unit permits Shares, Units, Contracts, Custom
```

```java
Quantity shares    = Quantity.ofShares(new BigDecimal("100"));
Quantity contracts = Quantity.of(new BigDecimal("10"), Contracts.INSTANCE);
Quantity widgets   = Quantity.of(new BigDecimal("50"), new Custom("widgets"));
shares.plus(Quantity.ofShares(new BigDecimal("250")));   // 350 shares
shares.plus(contracts);                                  // throws UnitMismatchException
```

Mismatched-unit arithmetic throws `UnitMismatchException`.

## Fund domain entities

Records carrying string-id references; open for extension via the schema mechanism in WP-11.

| Type | Key fields |
|---|---|
| `Fund` | `id, name, baseCurrency, inceptionDate, calendar` |
| `ShareClass` | `id, fundId, name, currency, managementFeeRate, hurdleRate (Optional)` |
| `Series` | `id, shareClassId, name, issueDate, issuePrice` |
| `Investor` | `id, name, jurisdiction, taxStatus` |
| `Position` | `fundId, instrumentId, quantity, costBasis` |
| `Transaction` | `id, type (TransactionType), tradeDate, settleDate, amount, partyIds` |
| `Cashflow` | `date, direction (CashflowDirection), amount, classification` |
| `NAV` | `fundId, asOfDate, grossAssets, liabilities, unitsOutstanding` — derived: `netAssets()`, `perUnit()` |
| `LedgerAccount` | `code, name, type (AccountType), currency` |

Enums: `TransactionType`, `CashflowDirection`, `AccountType`.

## Exceptions

- `CurrencyMismatchException` — `Money` arithmetic, `FxRate.apply` source mismatch
- `UnitMismatchException` — `Quantity` arithmetic with incompatible units
