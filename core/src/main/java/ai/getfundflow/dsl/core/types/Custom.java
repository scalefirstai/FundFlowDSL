package ai.getfundflow.dsl.core.types;

import java.util.Objects;

public record Custom(String name) implements Unit {

    public Custom {
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("custom unit name must not be blank");
        }
    }

    @Override
    public String label() {
        return name;
    }
}
