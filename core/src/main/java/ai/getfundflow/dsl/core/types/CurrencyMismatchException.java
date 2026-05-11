package ai.getfundflow.dsl.core.types;

import java.util.Currency;

public final class CurrencyMismatchException extends RuntimeException {

    private final Currency left;
    private final Currency right;

    public CurrencyMismatchException(Currency left, Currency right) {
        super("Currency mismatch: " + left.getCurrencyCode() + " vs " + right.getCurrencyCode());
        this.left = left;
        this.right = right;
    }

    public Currency left() {
        return left;
    }

    public Currency right() {
        return right;
    }
}
