package ai.getfundflow.dsl.core.calendar;

import java.time.LocalDate;

public record WeekendOnlyCalendar(String id) implements BusinessCalendar {

    public static final WeekendOnlyCalendar DEFAULT = new WeekendOnlyCalendar("WEEKEND-ONLY");

    @Override
    public boolean isHoliday(LocalDate date) {
        return false;
    }
}
