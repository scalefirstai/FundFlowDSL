package ai.getfundflow.dsl.lsp;

import ai.getfundflow.dsl.semantic.Severity;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;

public final class LspDiagnostics {

    private LspDiagnostics() {}

    public static List<Diagnostic> toLsp(List<ai.getfundflow.dsl.semantic.Diagnostic> in) {
        List<Diagnostic> out = new ArrayList<>(in.size());
        for (ai.getfundflow.dsl.semantic.Diagnostic d : in) {
            Diagnostic lsp = new Diagnostic();
            lsp.setRange(Positions.toRange(d.location()));
            lsp.setSeverity(toSeverity(d.severity()));
            lsp.setCode(d.code().code());
            lsp.setSource("fundflow");
            StringBuilder msg = new StringBuilder(d.message());
            d.hint().ifPresent(h -> msg.append("\n  help: ").append(h));
            lsp.setMessage(msg.toString());
            out.add(lsp);
        }
        return out;
    }

    private static DiagnosticSeverity toSeverity(Severity s) {
        return switch (s) {
            case ERROR -> DiagnosticSeverity.Error;
            case WARNING -> DiagnosticSeverity.Warning;
            case INFO -> DiagnosticSeverity.Information;
        };
    }
}
