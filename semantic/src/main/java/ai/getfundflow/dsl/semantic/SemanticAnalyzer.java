package ai.getfundflow.dsl.semantic;

import ai.getfundflow.dsl.ast.Program;

public final class SemanticAnalyzer {

    public SemanticResult analyze(Program program) {
        return analyze(program, SourceMap.EMPTY);
    }

    public SemanticResult analyze(Program program, SourceMap sourceMap) {
        Diagnostics diagnostics = new Diagnostics();
        SymbolTable symbols = new SymbolTable();
        TypeRegistry types = new TypeRegistry();

        new SymbolCollector(symbols, diagnostics, sourceMap, types).collect(program);
        new TypeChecker(symbols, diagnostics, sourceMap, types).check(program);
        new EffectivityValidator(diagnostics, sourceMap).validate(program);

        return new SemanticResult(symbols, types, diagnostics);
    }

    public record SemanticResult(SymbolTable symbols, TypeRegistry types, Diagnostics diagnostics) {

        /** Legacy two-arg constructor for callers that don't carry a registry. */
        public SemanticResult(SymbolTable symbols, Diagnostics diagnostics) {
            this(symbols, new TypeRegistry(), diagnostics);
        }

        public boolean ok() {
            return !diagnostics.hasErrors();
        }
    }
}
