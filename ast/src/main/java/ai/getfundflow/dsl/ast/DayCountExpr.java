package ai.getfundflow.dsl.ast;

import ai.getfundflow.dsl.core.types.DayCount;

public sealed interface DayCountExpr permits DayCountExpr.Literal, DayCountExpr.Reference {

    record Literal(DayCount value) implements DayCountExpr {}

    record Reference(QualifiedRef ref) implements DayCountExpr {}
}
