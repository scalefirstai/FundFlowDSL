package ai.getfundflow.dsl.ast;

public record LetBinding(String name, Expression value)
        implements RuleClause, WaterfallDecl.WaterfallBody {}
