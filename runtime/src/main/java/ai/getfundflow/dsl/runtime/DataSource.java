package ai.getfundflow.dsl.runtime;

import java.time.LocalDate;
import java.util.Optional;

public interface DataSource {

    Optional<RuntimeValue> lookup(String qualifiedName);

    default Optional<RuntimeValue> lookupAsOf(String qualifiedName, LocalDate asOf) {
        return lookup(qualifiedName);
    }

    static DataSource empty() {
        return new DataSource() {
            @Override
            public Optional<RuntimeValue> lookup(String qualifiedName) {
                return Optional.empty();
            }
        };
    }
}
