package ai.getfundflow.dsl.core.types;

import java.math.BigDecimal;
import java.time.LocalDate;

public sealed interface DayCount
        permits Actual360, Actual365, Thirty360, ActualActual {

    BigDecimal yearFraction(LocalDate start, LocalDate end);

    String code();
}
