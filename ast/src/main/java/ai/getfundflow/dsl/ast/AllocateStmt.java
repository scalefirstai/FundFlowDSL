package ai.getfundflow.dsl.ast;

public record AllocateStmt(Expression amount, Expression target, AllocationMethod method)
        implements Statement {}
