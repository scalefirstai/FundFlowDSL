package ai.getfundflow.dsl.agent;

import ai.getfundflow.dsl.semantic.SymbolTable;
import ai.getfundflow.dsl.semantic.TypeRegistry;
import ai.getfundflow.dsl.stdlib.FunctionRegistry;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Combined type + symbol catalog used to ground the AI agent (spec §14.1).
 *
 * <p>Composed of:
 * <ul>
 *   <li>The DSL's built-in base types and field-type names ({@link TypeRegistry})</li>
 *   <li>The extensions declared in the analyzed program plus their fields</li>
 *   <li>The in-scope rules / schedules / waterfalls / policies (by name)</li>
 *   <li>The stdlib {@link FunctionRegistry}</li>
 * </ul>
 *
 * <p>The agent is forbidden from inventing identifiers (§14.2). Every reference it
 * emits must resolve against one of the names in this catalog.
 */
public final class Catalog {

    private final Set<String> baseTypes;
    private final Set<String> fieldTypes;
    private final Map<String, ExtensionEntry> extensions;
    private final Map<String, DeclEntry> declarations;
    private final Map<String, FunctionEntry> functions;

    private Catalog(
            Set<String> baseTypes,
            Set<String> fieldTypes,
            Map<String, ExtensionEntry> extensions,
            Map<String, DeclEntry> declarations,
            Map<String, FunctionEntry> functions) {
        this.baseTypes = Set.copyOf(baseTypes);
        this.fieldTypes = Set.copyOf(fieldTypes);
        this.extensions = Map.copyOf(extensions);
        this.declarations = Map.copyOf(declarations);
        this.functions = Map.copyOf(functions);
    }

    public static Catalog build(SymbolTable symbols, TypeRegistry types) {
        // Built-in types
        Set<String> baseTypes = new LinkedHashSet<>(TypeRegistry.baseTypes());
        Set<String> fieldTypes = new LinkedHashSet<>(TypeRegistry.fieldTypeNames());

        // User extensions
        Map<String, ExtensionEntry> exts = new TreeMap<>();
        types.extensions().forEach((name, info) -> {
            Map<String, String> fields = new TreeMap<>();
            info.fields().forEach((fieldName, type) -> fields.put(fieldName, type.describe()));
            exts.put(name, new ExtensionEntry(info.baseType(), fields));
        });

        // In-scope declarations
        Map<String, DeclEntry> decls = new TreeMap<>();
        if (symbols != null) {
            symbols.declarations().forEach((name, symbol) -> {
                String kind = symbol.getClass().getSimpleName().replace("Symbol", "").toLowerCase();
                decls.put(name, new DeclEntry(kind));
            });
        }

        // Stdlib functions
        Map<String, FunctionEntry> fns = new TreeMap<>();
        for (String name : FunctionRegistry.names()) {
            FunctionRegistry.Signature sig = FunctionRegistry.lookup(name).orElseThrow();
            fns.put(name, new FunctionEntry(
                    sig.category().name().toLowerCase(),
                    sig.summary(),
                    sig.minArity(),
                    sig.maxArity() == Integer.MAX_VALUE ? -1 : sig.maxArity()));
        }

        return new Catalog(baseTypes, fieldTypes, exts, decls, fns);
    }

    public Set<String> baseTypes() { return baseTypes; }
    public Set<String> fieldTypes() { return fieldTypes; }
    public Map<String, ExtensionEntry> extensions() { return extensions; }
    public Map<String, DeclEntry> declarations() { return declarations; }
    public Map<String, FunctionEntry> functions() { return functions; }

    /** Materialize to plain Java collections suitable for the CLI's JSON writer. */
    public Map<String, Object> toJsonShape() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("baseTypes", List.copyOf(baseTypes));
        root.put("fieldTypes", List.copyOf(fieldTypes));

        Map<String, Object> extMap = new TreeMap<>();
        extensions.forEach((name, ext) -> {
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("extends", ext.baseType);
            e.put("fields", ext.fields);
            extMap.put(name, e);
        });
        root.put("extensions", extMap);

        Map<String, Object> declMap = new TreeMap<>();
        declarations.forEach((name, decl) -> {
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("kind", decl.kind);
            declMap.put(name, d);
        });
        root.put("declarations", declMap);

        Map<String, Object> fnMap = new TreeMap<>();
        functions.forEach((name, fn) -> {
            Map<String, Object> f = new LinkedHashMap<>();
            f.put("category", fn.category);
            f.put("summary", fn.summary);
            f.put("minArity", fn.minArity);
            f.put("maxArity", fn.maxArity);
            fnMap.put(name, f);
        });
        root.put("functions", fnMap);

        return root;
    }

    public record ExtensionEntry(String baseType, Map<String, String> fields) {
        public ExtensionEntry {
            fields = Map.copyOf(fields);
        }
    }

    public record DeclEntry(String kind) {}

    public record FunctionEntry(String category, String summary, int minArity, int maxArity) {}
}
