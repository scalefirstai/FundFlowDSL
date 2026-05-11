package ai.getfundflow.dsl.core.types;

import java.time.LocalDate;
import java.util.Objects;

public record Series(
        String id,
        String shareClassId,
        String name,
        LocalDate issueDate,
        Money issuePrice) {

    public Series {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(shareClassId, "shareClassId");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(issueDate, "issueDate");
        Objects.requireNonNull(issuePrice, "issuePrice");
    }
}
