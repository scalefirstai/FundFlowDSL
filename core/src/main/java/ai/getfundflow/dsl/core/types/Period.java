package ai.getfundflow.dsl.core.types;

import ai.getfundflow.dsl.core.calendar.BusinessCalendar;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

public sealed interface Period
        permits CalendarPeriod, NamedPeriod, RelativePeriod {

    LocalDate start();

    LocalDate end();

    default long days() {
        return ChronoUnit.DAYS.between(start(), end()) + 1;
    }

    default long businessDays(BusinessCalendar cal) {
        long count = 0;
        LocalDate cursor = start();
        LocalDate end = end();
        while (!cursor.isAfter(end)) {
            if (cal.isBusinessDay(cursor)) {
                count++;
            }
            cursor = cursor.plusDays(1);
        }
        return count;
    }

    default boolean contains(LocalDate date) {
        return !date.isBefore(start()) && !date.isAfter(end());
    }

    default Optional<Period> intersect(Period other) {
        LocalDate s = start().isAfter(other.start()) ? start() : other.start();
        LocalDate e = end().isBefore(other.end()) ? end() : other.end();
        if (s.isAfter(e)) {
            return Optional.empty();
        }
        return Optional.of(new CalendarPeriod(s, e));
    }
}
