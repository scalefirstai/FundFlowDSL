package ai.getfundflow.dsl.core.types;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;

public record Thirty360() implements DayCount {

    public static final Thirty360 INSTANCE = new Thirty360();
    private static final BigDecimal DENOMINATOR = new BigDecimal("360");

    @Override
    public BigDecimal yearFraction(LocalDate start, LocalDate end) {
        int d1 = Math.min(start.getDayOfMonth(), 30);
        int d2 = end.getDayOfMonth();
        if (d1 == 30 && d2 == 31) {
            d2 = 30;
        }
        long days = 360L * (end.getYear() - start.getYear())
                + 30L * (end.getMonthValue() - start.getMonthValue())
                + (d2 - d1);
        return BigDecimal.valueOf(days).divide(DENOMINATOR, MathContext.DECIMAL64);
    }

    @Override
    public String code() {
        return "30/360";
    }
}
