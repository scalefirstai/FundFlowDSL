package ai.getfundflow.dsl.core.types;

import java.time.LocalDate;
import java.util.Objects;

public record Cashflow(
        LocalDate date,
        CashflowDirection direction,
        Money amount,
        String classification) {

    public Cashflow {
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(classification, "classification");
    }
}
