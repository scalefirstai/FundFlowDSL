package ai.getfundflow.dsl.runtime;

import java.util.Map;

public record AuditEntry(
        String rule,
        String description,
        Map<String, RuntimeValue> inputs,
        RuntimeValue output) {

    public AuditEntry {
        inputs = Map.copyOf(inputs);
    }
}
