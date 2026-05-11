package ai.getfundflow.dsl.semantic;

import java.util.Currency;

public sealed interface DslType {

    String describe();

    record MoneyType(Currency currency) implements DslType {
        public static MoneyType anyCurrency() {
            return new MoneyType(null);
        }

        public boolean isAnyCurrency() {
            return currency == null;
        }

        @Override
        public String describe() {
            return isAnyCurrency() ? "Money" : "Money(" + currency.getCurrencyCode() + ")";
        }
    }

    record PercentageType() implements DslType {
        public static final PercentageType INSTANCE = new PercentageType();

        @Override
        public String describe() {
            return "Percentage";
        }
    }

    record BigDecimalType() implements DslType {
        public static final BigDecimalType INSTANCE = new BigDecimalType();

        @Override
        public String describe() {
            return "Number";
        }
    }

    record BooleanType() implements DslType {
        public static final BooleanType INSTANCE = new BooleanType();

        @Override
        public String describe() {
            return "Boolean";
        }
    }

    record BusinessDateType() implements DslType {
        public static final BusinessDateType INSTANCE = new BusinessDateType();

        @Override
        public String describe() {
            return "Date";
        }
    }

    record PeriodType() implements DslType {
        public static final PeriodType INSTANCE = new PeriodType();

        @Override
        public String describe() {
            return "Period";
        }
    }

    record DayCountType() implements DslType {
        public static final DayCountType INSTANCE = new DayCountType();

        @Override
        public String describe() {
            return "DayCount";
        }
    }

    record QuantityType() implements DslType {
        public static final QuantityType INSTANCE = new QuantityType();

        @Override
        public String describe() {
            return "Quantity";
        }
    }

    record StringType() implements DslType {
        public static final StringType INSTANCE = new StringType();

        @Override
        public String describe() {
            return "String";
        }
    }

    /** Used for unresolved phrasal references and unknown subexpressions. */
    record UnknownType() implements DslType {
        public static final UnknownType INSTANCE = new UnknownType();

        @Override
        public String describe() {
            return "?";
        }
    }
}
