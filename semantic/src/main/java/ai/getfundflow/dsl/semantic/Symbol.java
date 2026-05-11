package ai.getfundflow.dsl.semantic;

import ai.getfundflow.dsl.ast.PolicyDecl;
import ai.getfundflow.dsl.ast.RuleDecl;
import ai.getfundflow.dsl.ast.ScheduleDecl;
import ai.getfundflow.dsl.ast.TypeExtensionDecl;
import ai.getfundflow.dsl.ast.WaterfallDecl;

public sealed interface Symbol {

    String name();

    record RuleSymbol(String name, RuleDecl decl) implements Symbol {}

    record ScheduleSymbol(String name, ScheduleDecl decl) implements Symbol {}

    record WaterfallSymbol(String name, WaterfallDecl decl) implements Symbol {}

    record PolicySymbol(String name, PolicyDecl decl) implements Symbol {}

    record TypeExtensionSymbol(String name, TypeExtensionDecl decl) implements Symbol {}

    record BindingSymbol(String name, DslType type) implements Symbol {}
}
