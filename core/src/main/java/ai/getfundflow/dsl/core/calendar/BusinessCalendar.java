package ai.getfundflow.dsl.core.calendar;

import java.time.LocalDate;

public sealed interface BusinessCalendar
        permits WeekendOnlyCalendar {

    String id();

    boolean isHoliday(LocalDate date);

    default boolean isWeekend(LocalDate date) {
        return switch (date.getDayOfWeek()) {
            case SATURDAY, SUNDAY -> true;
            default -> false;
        };
    }

    default boolean isBusinessDay(LocalDate date) {
        return !isWeekend(date) && !isHoliday(date);
    }
}
