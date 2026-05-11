package ai.getfundflow.dsl.semantic;

import ai.getfundflow.dsl.ast.FieldDecl;
import ai.getfundflow.dsl.ast.PolicyDecl;
import ai.getfundflow.dsl.ast.Program;
import ai.getfundflow.dsl.ast.RuleDecl;
import ai.getfundflow.dsl.ast.ScheduleDecl;
import ai.getfundflow.dsl.ast.TopLevelDecl;
import ai.getfundflow.dsl.ast.TypeExtensionDecl;
import ai.getfundflow.dsl.ast.WaterfallDecl;
import ai.getfundflow.dsl.semantic.Symbol.PolicySymbol;
import ai.getfundflow.dsl.semantic.Symbol.RuleSymbol;
import ai.getfundflow.dsl.semantic.Symbol.ScheduleSymbol;
import ai.getfundflow.dsl.semantic.Symbol.TypeExtensionSymbol;
import ai.getfundflow.dsl.semantic.Symbol.WaterfallSymbol;
import java.util.LinkedHashMap;
import java.util.Map;

public final class SymbolCollector {

    private final SymbolTable table;
    private final Diagnostics diagnostics;
    private final SourceMap sourceMap;
    private final TypeRegistry types;

    public SymbolCollector(SymbolTable table, Diagnostics diagnostics) {
        this(table, diagnostics, SourceMap.EMPTY, new TypeRegistry());
    }

    public SymbolCollector(SymbolTable table, Diagnostics diagnostics, SourceMap sourceMap) {
        this(table, diagnostics, sourceMap, new TypeRegistry());
    }

    public SymbolCollector(SymbolTable table, Diagnostics diagnostics, SourceMap sourceMap, TypeRegistry types) {
        this.table = table;
        this.diagnostics = diagnostics;
        this.sourceMap = sourceMap;
        this.types = types;
    }

    public void collect(Program program) {
        for (TopLevelDecl decl : program.declarations()) {
            collectDecl(decl);
        }
    }

    private void collectDecl(TopLevelDecl decl) {
        Symbol symbol = switch (decl) {
            case RuleDecl r -> new RuleSymbol(r.name(), r);
            case ScheduleDecl s -> new ScheduleSymbol(s.name(), s);
            case WaterfallDecl w -> new WaterfallSymbol(w.name(), w);
            case PolicyDecl p -> new PolicySymbol(p.name(), p);
            case TypeExtensionDecl t -> {
                registerExtension(t);
                yield new TypeExtensionSymbol(t.typeName(), t);
            }
        };
        if (!table.register(symbol)) {
            diagnostics.add(Diagnostic.of(
                    DiagnosticCode.DUPLICATE_DECLARATION,
                    sourceMap.locationOf(decl),
                    "duplicate declaration: '" + symbol.name() + "'"));
        }
    }

    private void registerExtension(TypeExtensionDecl decl) {
        String base = decl.baseType();
        String canonicalBase = types.canonicalBase(base).orElse(null);
        if (canonicalBase == null) {
            diagnostics.add(Diagnostic.of(
                    DiagnosticCode.UNKNOWN_BASE_TYPE,
                    sourceMap.locationOf(decl),
                    "extension '" + decl.typeName() + "' extends unknown base type '" + base + "'"));
            return;
        }
        Map<String, DslType> fields = new LinkedHashMap<>();
        for (FieldDecl field : decl.fields()) {
            DslType ft = types.resolveFieldType(field.type().name()).orElse(null);
            if (ft == null) {
                diagnostics.add(Diagnostic.of(
                        DiagnosticCode.UNKNOWN_FIELD_TYPE,
                        sourceMap.locationOf(field),
                        "field '" + field.name() + "' has unknown type '"
                                + field.type().name() + "'"));
                continue;
            }
            fields.put(field.name(), ft);
        }
        types.registerExtension(new TypeRegistry.ExtensionInfo(decl.typeName(), canonicalBase, fields));
    }
}
