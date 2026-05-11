package ai.getfundflow.dsl.ast;

import java.util.Optional;

public record OverExpr(Expression expression, PeriodExpr period, Optional<DayCountExpr> dayCount)
        implements Expression {}
