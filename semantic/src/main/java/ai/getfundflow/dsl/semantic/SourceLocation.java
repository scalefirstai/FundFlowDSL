package ai.getfundflow.dsl.semantic;

public record SourceLocation(String file, int line, int column, int length) {

    public static final SourceLocation UNKNOWN = new SourceLocation("<unknown>", 0, 0, 0);

    public static SourceLocation of(String file, int line, int column, int length) {
        return new SourceLocation(file, line, column, length);
    }

    @Override
    public String toString() {
        return file + ":" + line + ":" + column;
    }
}
