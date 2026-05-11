package ai.getfundflow.dsl.lsp;

import ai.getfundflow.dsl.semantic.SourceLocation;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

/** LSP positions are 0-based; our {@link SourceLocation} is 1-based. */
public final class Positions {

    private Positions() {}

    public static Range toRange(SourceLocation loc) {
        Position start = new Position(Math.max(loc.line() - 1, 0), Math.max(loc.column() - 1, 0));
        Position end = new Position(start.getLine(), start.getCharacter() + Math.max(loc.length(), 1));
        return new Range(start, end);
    }

    public static boolean contains(SourceLocation loc, Position cursor) {
        int line = cursor.getLine() + 1;
        int col = cursor.getCharacter() + 1;
        if (loc.line() != line) return false;
        return col >= loc.column() && col <= loc.column() + Math.max(loc.length(), 1);
    }
}
