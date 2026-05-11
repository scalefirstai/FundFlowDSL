package ai.getfundflow.dsl.core.types;

import static org.assertj.core.api.Assertions.assertThat;

import ai.getfundflow.dsl.core.calendar.WeekendOnlyCalendar;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class BusinessDateTest {

    private static final WeekendOnlyCalendar CAL = WeekendOnlyCalendar.DEFAULT;

    @Test
    void mondayIsBusinessDay() {
        BusinessDate bd = new BusinessDate(LocalDate.of(2026, 3, 30), CAL);
        assertThat(bd.isBusinessDay()).isTrue();
    }

    @Test
    void saturdayIsNotBusinessDay() {
        BusinessDate bd = new BusinessDate(LocalDate.of(2026, 3, 28), CAL);
        assertThat(bd.isBusinessDay()).isFalse();
    }

    @Test
    void plusBusinessDaysSkipsWeekend() {
        BusinessDate friday = new BusinessDate(LocalDate.of(2026, 3, 27), CAL);
        BusinessDate result = friday.plusBusinessDays(1);
        assertThat(result.date()).isEqualTo(LocalDate.of(2026, 3, 30));
    }

    @Test
    void plusFiveBusinessDaysIsOneWeek() {
        BusinessDate monday = new BusinessDate(LocalDate.of(2026, 3, 23), CAL);
        BusinessDate result = monday.plusBusinessDays(5);
        assertThat(result.date()).isEqualTo(LocalDate.of(2026, 3, 30));
    }

    @Test
    void previousBusinessDayFromMondayIsFriday() {
        BusinessDate monday = new BusinessDate(LocalDate.of(2026, 3, 30), CAL);
        assertThat(monday.previousBusinessDay().date()).isEqualTo(LocalDate.of(2026, 3, 27));
    }

    @Test
    void plusZeroIsIdentity() {
        BusinessDate bd = new BusinessDate(LocalDate.of(2026, 3, 30), CAL);
        assertThat(bd.plusBusinessDays(0)).isEqualTo(bd);
    }

    @Test
    void plusBusinessDaysFromWeekendLandsOnBusinessDay() {
        BusinessDate saturday = new BusinessDate(LocalDate.of(2026, 3, 28), CAL);
        BusinessDate result = saturday.plusBusinessDays(1);
        assertThat(result.date()).isEqualTo(LocalDate.of(2026, 3, 30));
    }
}
