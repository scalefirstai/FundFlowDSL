package ai.getfundflow.dsl.ast;

public sealed interface RuleClause
        permits DescriptionClause, AppliesToClause, EffectiveClause, LetBinding, Statement {
}
