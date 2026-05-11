package ai.getfundflow.dsl.core.types;

import java.time.LocalDate;
import java.time.Month;
import java.time.YearMonth;
import java.util.Objects;

public record CalendarPeriod(LocalDate start, LocalDate end) implements Period {

    public CalendarPeriod {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("end (" + end + ") is before start (" + start + ")");
        }
    }

    public static CalendarPeriod ofMonth(int year, Month month) {
        YearMonth ym = YearMonth.of(year, month);
        return new CalendarPeriod(ym.atDay(1), ym.atEndOfMonth());
    }

    public static CalendarPeriod ofQuarter(int year, int quarter) {
        if (quarter < 1 || quarter > 4) {
            throw new IllegalArgumentException("quarter must be 1..4: " + quarter);
        }
        Month firstMonth = Month.of((quarter - 1) * 3 + 1);
        YearMonth start = YearMonth.of(year, firstMonth);
        YearMonth end = start.plusMonths(2);
        return new CalendarPeriod(start.atDay(1), end.atEndOfMonth());
    }

    public static CalendarPeriod ofYear(int year) {
        return new CalendarPeriod(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));
    }
}
