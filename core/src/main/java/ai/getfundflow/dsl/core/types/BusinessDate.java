package ai.getfundflow.dsl.core.types;

import ai.getfundflow.dsl.core.calendar.BusinessCalendar;
import java.time.LocalDate;
import java.util.Objects;

public record BusinessDate(LocalDate date, BusinessCalendar calendar) {

    public BusinessDate {
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(calendar, "calendar");
    }

    public boolean isBusinessDay() {
        return calendar.isBusinessDay(date);
    }

    public BusinessDate plusBusinessDays(int n) {
        if (n == 0) {
            return this;
        }
        int step = n > 0 ? 1 : -1;
        int remaining = Math.abs(n);
        LocalDate cursor = date;
        while (remaining > 0) {
            cursor = cursor.plusDays(step);
            if (calendar.isBusinessDay(cursor)) {
                remaining--;
            }
        }
        return new BusinessDate(cursor, calendar);
    }

    public BusinessDate previousBusinessDay() {
        return plusBusinessDays(-1);
    }

    public BusinessDate nextBusinessDay() {
        return plusBusinessDays(1);
    }
}
