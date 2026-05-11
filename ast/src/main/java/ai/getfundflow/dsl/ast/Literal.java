package ai.getfundflow.dsl.ast;

import ai.getfundflow.dsl.core.types.DayCount;
import ai.getfundflow.dsl.core.types.Money;
import ai.getfundflow.dsl.core.types.Percentage;
import java.math.BigDecimal;
import java.time.LocalDate;

public sealed interface Literal extends Expression
        permits Literal.MoneyLit,
                Literal.DateLit,
                Literal.PercentLit,
                Literal.BpsLit,
                Literal.DayCountLit,
                Literal.NumberLit,
                Literal.StringLit {

    record MoneyLit(Money value) implements Literal {}

    record DateLit(LocalDate value) implements Literal {}

    record PercentLit(Percentage value) implements Literal {}

    record BpsLit(BigDecimal bps) implements Literal {}

    record DayCountLit(DayCount value) implements Literal {}

    record NumberLit(BigDecimal value) implements Literal {}

    record StringLit(String value) implements Literal {}
}
