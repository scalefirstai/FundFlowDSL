package ai.getfundflow.dsl.core.types;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public record Actual365() implements DayCount {

    public static final Actual365 INSTANCE = new Actual365();
    private static final BigDecimal DENOMINATOR = new BigDecimal("365");

    @Override
    public BigDecimal yearFraction(LocalDate start, LocalDate end) {
        long days = ChronoUnit.DAYS.between(start, end);
        return BigDecimal.valueOf(days).divide(DENOMINATOR, MathContext.DECIMAL64);
    }

    @Override
    public String code() {
        return "actual/365";
    }
}
