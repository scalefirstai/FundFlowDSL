package ai.getfundflow.dsl.stdlib;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

public final class DateFunctions {

    private DateFunctions() {}

    public static int year(LocalDate date) {
        return date.getYear();
    }

    public static int month(LocalDate date) {
        return date.getMonthValue();
    }

    public static int day(LocalDate date) {
        return date.getDayOfMonth();
    }

    public static LocalDate edate(LocalDate date, int monthsOffset) {
        return date.plusMonths(monthsOffset);
    }

    public static LocalDate eomonth(LocalDate date, int monthsOffset) {
        YearMonth ym = YearMonth.from(date.plusMonths(monthsOffset));
        return ym.atEndOfMonth();
    }

    public static long datediff(LocalDate start, LocalDate end, String unit) {
        return switch (unit.toLowerCase(Locale.ROOT)) {
            case "days", "d" -> ChronoUnit.DAYS.between(start, end);
            case "weeks", "w" -> ChronoUnit.WEEKS.between(start, end);
            case "months", "m" -> ChronoUnit.MONTHS.between(start, end);
            case "years", "y" -> ChronoUnit.YEARS.between(start, end);
            default -> throw new IllegalArgumentException("datediff: unknown unit '" + unit + "'");
        };
    }
}
