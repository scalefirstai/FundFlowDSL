package ai.getfundflow.dsl.core.types;

import java.util.Objects;

public record Investor(
        String id,
        String name,
        String jurisdiction,
        String taxStatus) {

    public Investor {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(jurisdiction, "jurisdiction");
        Objects.requireNonNull(taxStatus, "taxStatus");
    }
}
