package ai.getfundflow.dsl.core.types;

import java.util.Currency;
import java.util.Objects;
import java.util.Optional;

public record ShareClass(
        String id,
        String fundId,
        String name,
        Currency currency,
        Percentage managementFeeRate,
        Optional<Percentage> hurdleRate) {

    public ShareClass {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(fundId, "fundId");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(managementFeeRate, "managementFeeRate");
        Objects.requireNonNull(hurdleRate, "hurdleRate");
    }
}
