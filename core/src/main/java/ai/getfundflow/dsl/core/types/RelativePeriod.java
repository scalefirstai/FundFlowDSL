package ai.getfundflow.dsl.core.types;

import java.time.LocalDate;
import java.util.Objects;

public record RelativePeriod(LocalDate anchor, int length, Direction direction) implements Period {

    public enum Direction { BACKWARD, FORWARD }

    public RelativePeriod {
        Objects.requireNonNull(anchor, "anchor");
        Objects.requireNonNull(direction, "direction");
        if (length < 0) {
            throw new IllegalArgumentException("length must be non-negative; use Direction to indicate orientation");
        }
    }

    public static RelativePeriod trailing(LocalDate anchor, int length) {
        return new RelativePeriod(anchor, length, Direction.BACKWARD);
    }

    public static RelativePeriod leading(LocalDate anchor, int length) {
        return new RelativePeriod(anchor, length, Direction.FORWARD);
    }

    @Override
    public LocalDate start() {
        return direction == Direction.BACKWARD ? anchor.minusDays(length) : anchor;
    }

    @Override
    public LocalDate end() {
        return direction == Direction.FORWARD ? anchor.plusDays(length) : anchor;
    }
}
