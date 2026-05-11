package ai.getfundflow.dsl.core.types;

import ai.getfundflow.dsl.core.calendar.BusinessCalendar;
import java.time.LocalDate;
import java.util.Currency;
import java.util.Objects;

public record Fund(
        String id,
        String name,
        Currency baseCurrency,
        LocalDate inceptionDate,
        BusinessCalendar calendar) {

    public Fund {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(baseCurrency, "baseCurrency");
        Objects.requireNonNull(inceptionDate, "inceptionDate");
        Objects.requireNonNull(calendar, "calendar");
    }
}
