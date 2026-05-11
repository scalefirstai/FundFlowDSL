package ai.getfundflow.dsl.runtime;

import java.util.List;
import java.util.Map;

public record EvaluationResult(
        Map<String, RuntimeValue> outputs,
        List<LedgerEntry> postings,
        AuditTrail trail) {

    public EvaluationResult {
        outputs = Map.copyOf(outputs);
        postings = List.copyOf(postings);
    }
}
