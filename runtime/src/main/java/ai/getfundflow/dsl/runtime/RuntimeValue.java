package ai.getfundflow.dsl.runtime;

import ai.getfundflow.dsl.core.types.DayCount;
import ai.getfundflow.dsl.core.types.Money;
import ai.getfundflow.dsl.core.types.Percentage;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public sealed interface RuntimeValue
        permits RuntimeValue.MoneyVal, RuntimeValue.PercentVal, RuntimeValue.NumberVal,
                RuntimeValue.BoolVal, RuntimeValue.DateVal, RuntimeValue.PeriodVal,
                RuntimeValue.DayCountVal, RuntimeValue.StringVal, RuntimeValue.ListVal,
                RuntimeValue.NullVal {

    record MoneyVal(Money value) implements RuntimeValue {}

    record PercentVal(Percentage value) implements RuntimeValue {}

    record NumberVal(BigDecimal value) implements RuntimeValue {}

    record BoolVal(boolean value) implements RuntimeValue {}

    record DateVal(LocalDate value) implements RuntimeValue {}

    record PeriodVal(LocalDate start, LocalDate end) implements RuntimeValue {}

    record DayCountVal(DayCount value) implements RuntimeValue {}

    record StringVal(String value) implements RuntimeValue {}

    record ListVal(List<RuntimeValue> values) implements RuntimeValue {
        public ListVal {
            values = List.copyOf(values);
        }
    }

    record NullVal() implements RuntimeValue {
        public static final NullVal INSTANCE = new NullVal();
    }
}
