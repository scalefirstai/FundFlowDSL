package ai.getfundflow.dsl.ast;

import java.util.Optional;

public sealed interface PeriodExpr
        permits PeriodExpr.ExplicitFromTo, PeriodExpr.FromInception, PeriodExpr.NamedOrPhrasal {

    record ExplicitFromTo(DateExpr start, Optional<DateExpr> end) implements PeriodExpr {}

    record FromInception() implements PeriodExpr {
        public static final FromInception INSTANCE = new FromInception();
    }

    record NamedOrPhrasal(QualifiedRef name) implements PeriodExpr {}
}
