package ai.getfundflow.dsl.agent;

import ai.getfundflow.dsl.ast.Program;
import ai.getfundflow.dsl.parser.AstBuilder;
import ai.getfundflow.dsl.parser.ParseException;
import ai.getfundflow.dsl.parser.ParseHarness;
import ai.getfundflow.dsl.parser.gen.FundFlowParser;
import ai.getfundflow.dsl.semantic.Diagnostic;
import ai.getfundflow.dsl.semantic.DiagnosticCode;
import ai.getfundflow.dsl.semantic.SemanticAnalyzer;
import ai.getfundflow.dsl.semantic.SourceLocation;
import ai.getfundflow.dsl.semantic.SourceMap;
import ai.getfundflow.dsl.semantic.SymbolTable;
import ai.getfundflow.dsl.semantic.TypeRegistry;
import java.util.ArrayList;
import java.util.List;

/**
 * Validates a candidate {@code .ff} program against the parse → semantic pipeline.
 *
 * <p>Returns three diagnostic arrays as required by spec §14.3:
 * <ul>
 *   <li>{@code parse} — parse errors (single, locatable per spec §5.3)</li>
 *   <li>{@code types} — semantic phase: FF1xxx type errors, FF2xxx symbol errors,
 *       FF4xxx determinism rules</li>
 *   <li>{@code eval} — runtime evaluation diagnostics (today: effectivity overlap,
 *       inverted periods, deferred phrasals). Eval-time errors (currency mismatch
 *       at runtime, etc.) are surfaced if the program would fail to evaluate.</li>
 * </ul>
 *
 * <p>This is purely local — no API calls — so it runs in milliseconds for the
 * canonical examples.
 */
public final class Validator {

    /** Result of validating one candidate program. */
    public record ValidationReport(
            List<Diagnostic> parse,
            List<Diagnostic> types,
            List<Diagnostic> eval,
            Program program,
            SymbolTable symbols,
            TypeRegistry typeRegistry) {

        public ValidationReport {
            parse = List.copyOf(parse);
            types = List.copyOf(types);
            eval = List.copyOf(eval);
        }

        public boolean ok() {
            return parse.isEmpty()
                    && types.stream().noneMatch(this::isFatal)
                    && eval.stream().noneMatch(this::isFatal);
        }

        private boolean isFatal(Diagnostic d) {
            return d.severity() == ai.getfundflow.dsl.semantic.Severity.ERROR;
        }

        public List<Diagnostic> errors() {
            List<Diagnostic> out = new ArrayList<>();
            for (Diagnostic d : parse) out.add(d);
            for (Diagnostic d : types) if (isFatal(d)) out.add(d);
            for (Diagnostic d : eval) if (isFatal(d)) out.add(d);
            return out;
        }
    }

    public ValidationReport validate(String source) {
        return validate(source, "candidate.ff");
    }

    public ValidationReport validate(String source, String fileName) {
        List<Diagnostic> parseDiagnostics = new ArrayList<>();
        AstBuilder builder = new AstBuilder();
        Program program = null;
        try {
            FundFlowParser.ProgramContext ctx =
                    (FundFlowParser.ProgramContext) ParseHarness.parse(source);
            program = builder.build(ctx, fileName);
        } catch (ParseException e) {
            parseDiagnostics.add(Diagnostic.error(
                    DiagnosticCode.TYPE_MISMATCH,
                    new SourceLocation(fileName, e.line(), e.charPositionInLine() + 1, 1),
                    "parse error: " + e.getMessage()));
        } catch (RuntimeException e) {
            parseDiagnostics.add(Diagnostic.error(
                    DiagnosticCode.TYPE_MISMATCH,
                    new SourceLocation(fileName, 1, 1, 1),
                    "parse failure: " + e.getMessage()));
        }

        if (program == null) {
            return new ValidationReport(
                    parseDiagnostics, List.of(), List.of(), null, null, new TypeRegistry());
        }

        SourceMap sourceMap = builder.sourceMap();
        SemanticAnalyzer.SemanticResult result = new SemanticAnalyzer().analyze(program, sourceMap);

        List<Diagnostic> typeErrors = new ArrayList<>();
        List<Diagnostic> evalErrors = new ArrayList<>();
        for (Diagnostic d : result.diagnostics().all()) {
            String code = d.code().code();
            if (code.startsWith("FF3")) {
                evalErrors.add(d);
            } else {
                typeErrors.add(d);
            }
        }

        return new ValidationReport(
                parseDiagnostics,
                typeErrors,
                evalErrors,
                program,
                result.symbols(),
                result.types());
    }
}
