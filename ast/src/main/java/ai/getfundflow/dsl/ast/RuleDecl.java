package ai.getfundflow.dsl.ast;

import java.util.List;

public record RuleDecl(String name, List<RuleClause> clauses) implements TopLevelDecl {
    public RuleDecl {
        clauses = List.copyOf(clauses);
    }
}
