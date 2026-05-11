package ai.getfundflow.dsl.ast;

public record AtBoundaryExpr(Expression expression, Boundary boundary, PeriodExpr period)
        implements Expression {

    public enum Boundary { START, END }
}
