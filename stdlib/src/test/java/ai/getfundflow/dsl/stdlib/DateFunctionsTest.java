package ai.getfundflow.dsl.stdlib;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class DateFunctionsTest {

    @Test
    void yearMonthDay() {
        LocalDate d = LocalDate.of(2026, 3, 15);
        assertThat(DateFunctions.year(d)).isEqualTo(2026);
        assertThat(DateFunctions.month(d)).isEqualTo(3);
        assertThat(DateFunctions.day(d)).isEqualTo(15);
    }

    @Test
    void edateForward() {
        assertThat(DateFunctions.edate(LocalDate.of(2026, 3, 15), 6))
                .isEqualTo(LocalDate.of(2026, 9, 15));
    }

    @Test
    void edateBackward() {
        assertThat(DateFunctions.edate(LocalDate.of(2026, 3, 15), -3))
                .isEqualTo(LocalDate.of(2025, 12, 15));
    }

    @Test
    void edateClampsAtEndOfMonth() {
        // Jan 31 + 1 month = Feb 28 (or 29 in leap year)
        assertThat(DateFunctions.edate(LocalDate.of(2026, 1, 31), 1))
                .isEqualTo(LocalDate.of(2026, 2, 28));
        assertThat(DateFunctions.edate(LocalDate.of(2024, 1, 31), 1))
                .isEqualTo(LocalDate.of(2024, 2, 29));
    }

    @Test
    void eomonthCurrent() {
        assertThat(DateFunctions.eomonth(LocalDate.of(2026, 3, 15), 0))
                .isEqualTo(LocalDate.of(2026, 3, 31));
    }

    @Test
    void eomonthOffset() {
        assertThat(DateFunctions.eomonth(LocalDate.of(2026, 3, 15), 3))
                .isEqualTo(LocalDate.of(2026, 6, 30));
        assertThat(DateFunctions.eomonth(LocalDate.of(2026, 3, 15), -1))
                .isEqualTo(LocalDate.of(2026, 2, 28));
    }

    @Test
    void datediffDays() {
        long d = DateFunctions.datediff(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), "days");
        assertThat(d).isEqualTo(364);
    }

    @Test
    void datediffMonths() {
        long m = DateFunctions.datediff(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), "months");
        assertThat(m).isEqualTo(11);
    }

    @Test
    void datediffYears() {
        long y = DateFunctions.datediff(
                LocalDate.of(2020, 6, 15), LocalDate.of(2026, 6, 15), "years");
        assertThat(y).isEqualTo(6);
    }

    @Test
    void datediffWeeks() {
        long w = DateFunctions.datediff(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 22), "weeks");
        assertThat(w).isEqualTo(3);
    }

    @Test
    void datediffShortAliases() {
        assertThat(DateFunctions.datediff(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 5), "d"))
                .isEqualTo(4);
    }

    @Test
    void datediffUnknownUnitThrows() {
        assertThatThrownBy(() -> DateFunctions.datediff(
                LocalDate.now(), LocalDate.now(), "fortnights"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
