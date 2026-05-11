package ai.getfundflow.dsl.core.types;

public final class UnitMismatchException extends RuntimeException {

    private final Unit left;
    private final Unit right;

    public UnitMismatchException(Unit left, Unit right) {
        super("Unit mismatch: " + left + " vs " + right);
        this.left = left;
        this.right = right;
    }

    public Unit left() {
        return left;
    }

    public Unit right() {
        return right;
    }
}
