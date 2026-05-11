package ai.getfundflow.dsl.runtime;

public interface AuditSink {

    void record(AuditEntry entry);

    static AuditSink discarding() {
        return entry -> { /* drop */ };
    }
}
