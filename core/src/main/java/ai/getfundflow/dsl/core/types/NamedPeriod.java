package ai.getfundflow.dsl.core.types;

import java.time.LocalDate;
import java.time.Month;
import java.util.Objects;

public record NamedPeriod(Kind kind, LocalDate asOf, LocalDate inception) implements Period {

    public enum Kind { YTD, MTD, QTD, SINCE_INCEPTION }

    public NamedPeriod {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(asOf, "asOf");
        if (kind == Kind.SINCE_INCEPTION && inception == null) {
            throw new IllegalArgumentException("SINCE_INCEPTION requires an inception date");
        }
    }

    public static NamedPeriod ytd(LocalDate asOf) {
        return new NamedPeriod(Kind.YTD, asOf, null);
    }

    public static NamedPeriod mtd(LocalDate asOf) {
        return new NamedPeriod(Kind.MTD, asOf, null);
    }

    public static NamedPeriod qtd(LocalDate asOf) {
        return new NamedPeriod(Kind.QTD, asOf, null);
    }

    public static NamedPeriod sinceInception(LocalDate asOf, LocalDate inception) {
        return new NamedPeriod(Kind.SINCE_INCEPTION, asOf, inception);
    }

    @Override
    public LocalDate start() {
        return switch (kind) {
            case YTD -> LocalDate.of(asOf.getYear(), 1, 1);
            case MTD -> asOf.withDayOfMonth(1);
            case QTD -> {
                int q = (asOf.getMonthValue() - 1) / 3;
                yield LocalDate.of(asOf.getYear(), Month.of(q * 3 + 1), 1);
            }
            case SINCE_INCEPTION -> inception;
        };
    }

    @Override
    public LocalDate end() {
        return asOf;
    }
}
