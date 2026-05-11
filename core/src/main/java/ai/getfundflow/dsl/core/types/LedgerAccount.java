package ai.getfundflow.dsl.core.types;

import java.util.Currency;
import java.util.Objects;

public record LedgerAccount(
        String code,
        String name,
        AccountType type,
        Currency currency) {

    public LedgerAccount {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(currency, "currency");
    }
}
