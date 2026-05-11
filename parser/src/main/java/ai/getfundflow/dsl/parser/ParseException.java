package ai.getfundflow.dsl.parser;

public final class ParseException extends RuntimeException {

    private final int line;
    private final int charPositionInLine;

    public ParseException(int line, int charPositionInLine, String message) {
        super("line " + line + ":" + charPositionInLine + " " + message);
        this.line = line;
        this.charPositionInLine = charPositionInLine;
    }

    public int line() {
        return line;
    }

    public int charPositionInLine() {
        return charPositionInLine;
    }
}
