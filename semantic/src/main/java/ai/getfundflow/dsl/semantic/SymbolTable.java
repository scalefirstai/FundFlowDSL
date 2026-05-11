package ai.getfundflow.dsl.semantic;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class SymbolTable {

    private final Map<String, Symbol> declarations = new HashMap<>();
    private final Map<String, Map<String, Symbol.BindingSymbol>> bindingsByOwner = new HashMap<>();

    public boolean register(Symbol symbol) {
        if (declarations.containsKey(symbol.name())) {
            return false;
        }
        declarations.put(symbol.name(), symbol);
        return true;
    }

    public Optional<Symbol> lookup(String name) {
        return Optional.ofNullable(declarations.get(name));
    }

    public boolean registerBinding(String owner, Symbol.BindingSymbol binding) {
        Map<String, Symbol.BindingSymbol> scope = bindingsByOwner.computeIfAbsent(owner, k -> new HashMap<>());
        if (scope.containsKey(binding.name())) {
            return false;
        }
        scope.put(binding.name(), binding);
        return true;
    }

    public Optional<Symbol.BindingSymbol> lookupBinding(String owner, String name) {
        Map<String, Symbol.BindingSymbol> scope = bindingsByOwner.get(owner);
        if (scope == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(scope.get(name));
    }

    public Map<String, Symbol> declarations() {
        return Collections.unmodifiableMap(declarations);
    }

    public Map<String, Symbol.BindingSymbol> bindingsFor(String owner) {
        Map<String, Symbol.BindingSymbol> scope = bindingsByOwner.get(owner);
        return scope == null ? Map.of() : Collections.unmodifiableMap(scope);
    }
}
