package ai.getfundflow.dsl.core.types;

import static org.assertj.core.api.Assertions.assertThat;

import ai.getfundflow.dsl.core.calendar.WeekendOnlyCalendar;
import java.time.LocalDate;
import java.time.Month;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PeriodTest {

    @Test
    void calendarPeriodForQuarter() {
        CalendarPeriod q1 = CalendarPeriod.ofQuarter(2026, 1);
        assertThat(q1.start()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(q1.end()).isEqualTo(LocalDate.of(2026, 3, 31));
        assertThat(q1.days()).isEqualTo(90);
    }

    @Test
    void calendarPeriodForMonth() {
        CalendarPeriod march = CalendarPeriod.ofMonth(2026, Month.MARCH);
        assertThat(march.start()).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(march.end()).isEqualTo(LocalDate.of(2026, 3, 31));
    }

    @Test
    void calendarPeriodForYear() {
        CalendarPeriod y = CalendarPeriod.ofYear(2026);
        assertThat(y.days()).isEqualTo(365);
    }

    @Test
    void businessDaysInQ1ExcludesWeekends() {
        CalendarPeriod q1 = CalendarPeriod.ofQuarter(2026, 1);
        long bd = q1.businessDays(WeekendOnlyCalendar.DEFAULT);
        assertThat(bd).isEqualTo(64);
    }

    @Test
    void containsBoundaryDates() {
        CalendarPeriod q1 = CalendarPeriod.ofQuarter(2026, 1);
        assertThat(q1.contains(LocalDate.of(2026, 1, 1))).isTrue();
        assertThat(q1.contains(LocalDate.of(2026, 3, 31))).isTrue();
        assertThat(q1.contains(LocalDate.of(2026, 4, 1))).isFalse();
    }

    @Test
    void intersectionIsIdempotent() {
        CalendarPeriod q1 = CalendarPeriod.ofQuarter(2026, 1);
        Optional<Period> self = q1.intersect(q1);
        assertThat(self).isPresent();
        assertThat(self.get().start()).isEqualTo(q1.start());
        assertThat(self.get().end()).isEqualTo(q1.end());
    }

    @Test
    void intersectionWithDisjointReturnsEmpty() {
        CalendarPeriod q1 = CalendarPeriod.ofQuarter(2026, 1);
        CalendarPeriod q3 = CalendarPeriod.ofQuarter(2026, 3);
        assertThat(q1.intersect(q3)).isEmpty();
    }

    @Test
    void intersectionOfOverlappingPeriods() {
        CalendarPeriod jan_apr = new CalendarPeriod(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 4, 30));
        CalendarPeriod feb_may = new CalendarPeriod(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 5, 31));
        Period overlap = jan_apr.intersect(feb_may).orElseThrow();
        assertThat(overlap.start()).isEqualTo(LocalDate.of(2026, 2, 1));
        assertThat(overlap.end()).isEqualTo(LocalDate.of(2026, 4, 30));
    }

    @Test
    void ytdResolvesToJanuaryFirstThroughAsOf() {
        NamedPeriod ytd = NamedPeriod.ytd(LocalDate.of(2026, 6, 15));
        assertThat(ytd.start()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(ytd.end()).isEqualTo(LocalDate.of(2026, 6, 15));
    }

    @Test
    void mtdResolvesToFirstOfMonth() {
        NamedPeriod mtd = NamedPeriod.mtd(LocalDate.of(2026, 6, 15));
        assertThat(mtd.start()).isEqualTo(LocalDate.of(2026, 6, 1));
    }

    @Test
    void qtdResolvesToQuarterStart() {
        NamedPeriod qtd = NamedPeriod.qtd(LocalDate.of(2026, 5, 15));
        assertThat(qtd.start()).isEqualTo(LocalDate.of(2026, 4, 1));
    }

    @Test
    void sinceInceptionUsesInceptionDate() {
        NamedPeriod si = NamedPeriod.sinceInception(LocalDate.of(2026, 6, 15), LocalDate.of(2020, 1, 1));
        assertThat(si.start()).isEqualTo(LocalDate.of(2020, 1, 1));
        assertThat(si.end()).isEqualTo(LocalDate.of(2026, 6, 15));
    }

    @Test
    void relativeBackwardPeriod() {
        RelativePeriod r = RelativePeriod.trailing(LocalDate.of(2026, 6, 15), 30);
        assertThat(r.start()).isEqualTo(LocalDate.of(2026, 5, 16));
        assertThat(r.end()).isEqualTo(LocalDate.of(2026, 6, 15));
    }
}
