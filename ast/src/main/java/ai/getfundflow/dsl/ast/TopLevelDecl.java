package ai.getfundflow.dsl.ast;

public sealed interface TopLevelDecl
        permits RuleDecl, ScheduleDecl, WaterfallDecl, PolicyDecl, TypeExtensionDecl {
}
