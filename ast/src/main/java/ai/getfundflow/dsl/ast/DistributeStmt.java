package ai.getfundflow.dsl.ast;

public record DistributeStmt(Expression amount, String waterfallName) implements Statement {}
