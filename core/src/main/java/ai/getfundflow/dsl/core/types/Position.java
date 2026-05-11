package ai.getfundflow.dsl.core.types;

import java.util.Objects;

public record Position(
        String fundId,
        String instrumentId,
        Quantity quantity,
        Money costBasis) {

    public Position {
        Objects.requireNonNull(fundId, "fundId");
        Objects.requireNonNull(instrumentId, "instrumentId");
        Objects.requireNonNull(quantity, "quantity");
        Objects.requireNonNull(costBasis, "costBasis");
    }
}
