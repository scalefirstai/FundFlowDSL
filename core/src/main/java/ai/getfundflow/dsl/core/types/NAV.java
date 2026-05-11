package ai.getfundflow.dsl.core.types;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.util.Objects;

public record NAV(
        String fundId,
        LocalDate asOfDate,
        Money grossAssets,
        Money liabilities,
        BigDecimal unitsOutstanding) {

    public NAV {
        Objects.requireNonNull(fundId, "fundId");
        Objects.requireNonNull(asOfDate, "asOfDate");
        Objects.requireNonNull(grossAssets, "grossAssets");
        Objects.requireNonNull(liabilities, "liabilities");
        Objects.requireNonNull(unitsOutstanding, "unitsOutstanding");
    }

    public Money netAssets() {
        return grossAssets.minus(liabilities);
    }

    public Money perUnit() {
        BigDecimal navPerUnit = netAssets().amount()
                .divide(unitsOutstanding, MathContext.DECIMAL64);
        return Money.of(navPerUnit, grossAssets.currency());
    }
}
