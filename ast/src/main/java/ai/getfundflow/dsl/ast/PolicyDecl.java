package ai.getfundflow.dsl.ast;

import java.util.List;

public record PolicyDecl(String name, List<RuleClause> clauses) implements TopLevelDecl {
    public PolicyDecl {
        clauses = List.copyOf(clauses);
    }
}
