package ai.getfundflow.dsl.runtime;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

public final class MapDataSource implements DataSource {

    private final Map<String, RuntimeValue> values;

    private MapDataSource(Map<String, RuntimeValue> values) {
        this.values = new TreeMap<>(values);
    }

    public static MapDataSource of(Map<String, RuntimeValue> values) {
        return new MapDataSource(values);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public Optional<RuntimeValue> lookup(String qualifiedName) {
        return Optional.ofNullable(values.get(qualifiedName));
    }

    public static final class Builder {
        private final Map<String, RuntimeValue> values = new LinkedHashMap<>();

        public Builder put(String name, RuntimeValue value) {
            values.put(name, value);
            return this;
        }

        public MapDataSource build() {
            return new MapDataSource(values);
        }
    }
}
