package ai.getfundflow.dsl.core.types;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Objects;

public record Quantity(BigDecimal value, Unit unit) {

    public Quantity {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(unit, "unit");
    }

    public static Quantity of(BigDecimal value, Unit unit) {
        return new Quantity(value, unit);
    }

    public static Quantity ofShares(BigDecimal value) {
        return new Quantity(value, Shares.INSTANCE);
    }

    public Quantity plus(Quantity other) {
        requireSameUnit(other);
        return new Quantity(value.add(other.value), unit);
    }

    public Quantity minus(Quantity other) {
        requireSameUnit(other);
        return new Quantity(value.subtract(other.value), unit);
    }

    public Quantity multiply(BigDecimal scalar) {
        return new Quantity(value.multiply(scalar, MathContext.DECIMAL64), unit);
    }

    public Quantity negate() {
        return new Quantity(value.negate(), unit);
    }

    private void requireSameUnit(Quantity other) {
        if (!unit.equals(other.unit)) {
            throw new UnitMismatchException(unit, other.unit);
        }
    }
}
