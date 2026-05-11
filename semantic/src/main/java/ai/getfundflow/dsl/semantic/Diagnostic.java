package ai.getfundflow.dsl.semantic;

import java.util.Optional;

public record Diagnostic(
        DiagnosticCode code,
        Severity severity,
        SourceLocation location,
        String message,
        Optional<String> hint) {

    public static Diagnostic error(DiagnosticCode code, SourceLocation location, String message) {
        return new Diagnostic(code, Severity.ERROR, location, message, Optional.empty());
    }

    public static Diagnostic warning(DiagnosticCode code, SourceLocation location, String message) {
        return new Diagnostic(code, Severity.WARNING, location, message, Optional.empty());
    }

    public static Diagnostic of(DiagnosticCode code, SourceLocation location, String message) {
        return new Diagnostic(code, code.defaultSeverity(), location, message, Optional.empty());
    }

    public Diagnostic withHint(String hint) {
        return new Diagnostic(code, severity, location, message, Optional.of(hint));
    }

    public String render() {
        StringBuilder sb = new StringBuilder();
        sb.append(severity.name().toLowerCase())
                .append('[').append(code.code()).append("]: ")
                .append(message)
                .append("\n  --> ").append(location);
        hint.ifPresent(h -> sb.append("\n  = help: ").append(h));
        return sb.toString();
    }
}
