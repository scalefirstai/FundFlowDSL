package ai.getfundflow.dsl.lsp;

import ai.getfundflow.dsl.ast.Program;
import ai.getfundflow.dsl.semantic.SemanticAnalyzer;
import ai.getfundflow.dsl.semantic.SourceMap;

/**
 * Snapshot of one document on the server side: the raw source plus the artefacts
 * derived from it. Recomputed on every {@code didOpen} / {@code didChange}.
 */
public record DocumentState(
        String uri,
        String source,
        Program program,
        SourceMap sourceMap,
        SemanticAnalyzer.SemanticResult semanticResult) {
}
