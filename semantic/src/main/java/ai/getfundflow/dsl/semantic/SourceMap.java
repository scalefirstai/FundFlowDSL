package ai.getfundflow.dsl.semantic;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Maps AST nodes to their original source locations, populated during AST construction.
 * Uses identity equality so value-equal AST records (e.g., two distinct {@code LetBinding("x", lit)})
 * keep distinct locations.
 */
public final class SourceMap {

    public static final SourceMap EMPTY = new SourceMap();

    private final Map<Object, SourceLocation> locations = new IdentityHashMap<>();

    public void put(Object node, SourceLocation location) {
        locations.put(node, location);
    }

    public SourceLocation locationOf(Object node) {
        return locations.getOrDefault(node, SourceLocation.UNKNOWN);
    }

    public Map<Object, SourceLocation> snapshot() {
        return Collections.unmodifiableMap(locations);
    }

    public int size() {
        return locations.size();
    }
}
