package ai.getfundflow.dsl.ast;

import java.util.List;

public record ScheduleDecl(String name, List<RuleClause> clauses) implements TopLevelDecl {
    public ScheduleDecl {
        clauses = List.copyOf(clauses);
    }
}
