package ai.getfundflow.dsl.ast;

public record AsOfExpr(Expression expression, DateExpr date) implements Expression {}
