package ai.getfundflow.dsl.core.types;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.time.Year;
import java.time.temporal.ChronoUnit;

public record ActualActual() implements DayCount {

    public static final ActualActual INSTANCE = new ActualActual();

    @Override
    public BigDecimal yearFraction(LocalDate start, LocalDate end) {
        if (start.equals(end)) {
            return BigDecimal.ZERO;
        }
        if (start.getYear() == end.getYear()) {
            return daysIn(start, end, start.getYear());
        }
        BigDecimal total = BigDecimal.ZERO;
        total = total.add(daysIn(start, LocalDate.of(start.getYear() + 1, 1, 1), start.getYear()));
        for (int y = start.getYear() + 1; y < end.getYear(); y++) {
            total = total.add(BigDecimal.ONE);
        }
        total = total.add(daysIn(LocalDate.of(end.getYear(), 1, 1), end, end.getYear()));
        return total;
    }

    private BigDecimal daysIn(LocalDate from, LocalDate to, int year) {
        long days = ChronoUnit.DAYS.between(from, to);
        BigDecimal denom = Year.isLeap(year) ? new BigDecimal("366") : new BigDecimal("365");
        return BigDecimal.valueOf(days).divide(denom, MathContext.DECIMAL64);
    }

    @Override
    public String code() {
        return "actual/actual";
    }
}
