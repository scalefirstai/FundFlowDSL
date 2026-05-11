package ai.getfundflow.dsl.ast;

public sealed interface Expression
        permits AsOfExpr, AtBoundaryExpr, OverExpr, PerAnnumExpr, NotExpr,
                BinaryOpExpr, FunctionCallExpr, AggregationCall, Literal, NameRef {
}
