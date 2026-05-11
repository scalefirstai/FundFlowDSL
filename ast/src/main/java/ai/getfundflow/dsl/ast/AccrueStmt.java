package ai.getfundflow.dsl.ast;

public record AccrueStmt(Expression rate, Expression basis, DayCountExpr dayCount)
        implements Statement {}
