package ai.getfundflow.dsl.core.types;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import ai.getfundflow.dsl.core.calendar.WeekendOnlyCalendar;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class FundEntitiesTest {

    private static final Currency USD = Currency.getInstance("USD");

    @Test
    void fundConstructsAndIsValueEqual() {
        Fund a = new Fund("F1", "Acme Master", USD, LocalDate.of(2020, 1, 1), WeekendOnlyCalendar.DEFAULT);
        Fund b = new Fund("F1", "Acme Master", USD, LocalDate.of(2020, 1, 1), WeekendOnlyCalendar.DEFAULT);
        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
    }

    @Test
    void shareClassRequiresNonNullFields() {
        assertThatNullPointerException().isThrownBy(
                () -> new ShareClass("SC1", "F1", "Class A", USD, null, Optional.empty()));
    }

    @Test
    void navComputesNetAssetsAndPerUnit() {
        NAV nav = new NAV(
                "F1",
                LocalDate.of(2026, 3, 31),
                Money.of("10000000", "USD"),
                Money.of("500000", "USD"),
                new BigDecimal("95000"));
        assertThat(nav.netAssets().amount()).isEqualByComparingTo("9500000.00");
        assertThat(nav.perUnit().amount()).isEqualByComparingTo("100");
    }

    @Test
    void transactionPartyIdsAreImmutable() {
        List<String> mutable = new java.util.ArrayList<>(List.of("INV1", "FUND1"));
        Transaction t = new Transaction(
                "T1",
                TransactionType.SUBSCRIPTION,
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 3, 3),
                Money.of("1000000", "USD"),
                mutable);
        mutable.clear();
        assertThat(t.partyIds()).containsExactly("INV1", "FUND1");
    }

    @Test
    void cashflowAndLedgerAccountConstruct() {
        Cashflow cf = new Cashflow(
                LocalDate.of(2026, 3, 31),
                CashflowDirection.OUTFLOW,
                Money.of("12500", "USD"),
                "management-fee");
        assertThat(cf.amount().amount()).isEqualByComparingTo("12500.00");

        LedgerAccount account = new LedgerAccount("4100", "Management Fee Expense", AccountType.EXPENSE, USD);
        assertThat(account.type()).isEqualTo(AccountType.EXPENSE);
    }
}
