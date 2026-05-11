package ai.getfundflow.dsl.ast;

public sealed interface Statement extends RuleClause, WaterfallDecl.WaterfallBody
        permits AccrueStmt, AllocateStmt, DistributeStmt, PostStmt, PublishStmt, WhenStmt {
}
