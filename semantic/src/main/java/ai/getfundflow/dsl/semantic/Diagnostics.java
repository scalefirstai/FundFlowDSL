package ai.getfundflow.dsl.semantic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Diagnostics {

    private final List<Diagnostic> entries = new ArrayList<>();

    public void add(Diagnostic diagnostic) {
        entries.add(diagnostic);
    }

    public List<Diagnostic> all() {
        return Collections.unmodifiableList(entries);
    }

    public List<Diagnostic> errors() {
        return entries.stream().filter(d -> d.severity() == Severity.ERROR).toList();
    }

    public List<Diagnostic> warnings() {
        return entries.stream().filter(d -> d.severity() == Severity.WARNING).toList();
    }

    public boolean hasErrors() {
        return entries.stream().anyMatch(d -> d.severity() == Severity.ERROR);
    }

    public int size() {
        return entries.size();
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }
}
