package ai.getfundflow.dsl.lsp;

import ai.getfundflow.dsl.ast.Program;
import ai.getfundflow.dsl.parser.AstBuilder;
import ai.getfundflow.dsl.parser.ParseException;
import ai.getfundflow.dsl.parser.ParseHarness;
import ai.getfundflow.dsl.parser.gen.FundFlowParser;
import ai.getfundflow.dsl.semantic.Diagnostic;
import ai.getfundflow.dsl.semantic.DiagnosticCode;
import ai.getfundflow.dsl.semantic.Diagnostics;
import ai.getfundflow.dsl.semantic.SemanticAnalyzer;
import ai.getfundflow.dsl.semantic.SourceLocation;
import ai.getfundflow.dsl.semantic.SourceMap;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class DocumentStore {

    private final ConcurrentMap<String, DocumentState> documents = new ConcurrentHashMap<>();

    public DocumentState analyze(String uri, String source) {
        AstBuilder builder = new AstBuilder();
        Diagnostics diagnostics = new Diagnostics();
        Program program = null;
        SourceMap sourceMap = SourceMap.EMPTY;
        try {
            FundFlowParser.ProgramContext ctx =
                    (FundFlowParser.ProgramContext) ParseHarness.parse(source);
            program = builder.build(ctx, uri);
            sourceMap = builder.sourceMap();
        } catch (ParseException e) {
            diagnostics.add(parseExceptionToDiagnostic(uri, e));
        } catch (RuntimeException e) {
            // ANTLR sometimes wraps errors via BailErrorStrategy
            diagnostics.add(Diagnostic.error(
                    DiagnosticCode.TYPE_MISMATCH,
                    new SourceLocation(uri, 1, 1, 1),
                    "parser failure: " + e.getMessage()));
        }

        SemanticAnalyzer.SemanticResult semantic = program == null
                ? null
                : new SemanticAnalyzer().analyze(program, sourceMap);

        // Combine parse-time and semantic-time diagnostics
        Diagnostics combined = new Diagnostics();
        diagnostics.all().forEach(combined::add);
        if (semantic != null) semantic.diagnostics().all().forEach(combined::add);

        DocumentState state = new DocumentState(uri, source, program, sourceMap,
                semantic == null
                        ? new SemanticAnalyzer.SemanticResult(null, combined)
                        : new SemanticAnalyzer.SemanticResult(semantic.symbols(), combined));
        documents.put(uri, state);
        return state;
    }

    public Optional<DocumentState> get(String uri) {
        return Optional.ofNullable(documents.get(uri));
    }

    public void remove(String uri) {
        documents.remove(uri);
    }

    private Diagnostic parseExceptionToDiagnostic(String uri, ParseException e) {
        SourceLocation loc = new SourceLocation(uri, e.line(), e.charPositionInLine() + 1, 1);
        return Diagnostic.error(DiagnosticCode.TYPE_MISMATCH, loc, "parse error: " + e.getMessage());
    }
}
