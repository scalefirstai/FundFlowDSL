package ai.getfundflow.dsl.core.types;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public record Transaction(
        String id,
        TransactionType type,
        LocalDate tradeDate,
        LocalDate settleDate,
        Money amount,
        List<String> partyIds) {

    public Transaction {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(tradeDate, "tradeDate");
        Objects.requireNonNull(settleDate, "settleDate");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(partyIds, "partyIds");
        partyIds = List.copyOf(partyIds);
    }
}
