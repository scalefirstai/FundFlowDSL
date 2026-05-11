package ai.getfundflow.dsl.runtime;

import ai.getfundflow.dsl.core.calendar.BusinessCalendar;
import ai.getfundflow.dsl.core.calendar.WeekendOnlyCalendar;
import ai.getfundflow.dsl.core.types.BusinessDate;

public record EvaluationContext(
        BusinessDate asOf,
        DataSource data,
        BusinessCalendar defaultCalendar,
        AuditSink audit) {

    public static EvaluationContext minimal(BusinessDate asOf) {
        return new EvaluationContext(
                asOf,
                DataSource.empty(),
                WeekendOnlyCalendar.DEFAULT,
                AuditSink.discarding());
    }
}
