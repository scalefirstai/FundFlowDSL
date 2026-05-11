package ai.getfundflow.dsl.core.types;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public record Actual360() implements DayCount {

    public static final Actual360 INSTANCE = new Actual360();
    private static final BigDecimal DENOMINATOR = new BigDecimal("360");

    @Override
    public BigDecimal yearFraction(LocalDate start, LocalDate end) {
        long days = ChronoUnit.DAYS.between(start, end);
        return BigDecimal.valueOf(days).divide(DENOMINATOR, MathContext.DECIMAL64);
    }

    @Override
    public String code() {
        return "actual/360";
    }
}
