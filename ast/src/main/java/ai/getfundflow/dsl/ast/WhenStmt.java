package ai.getfundflow.dsl.ast;

import java.util.Optional;

public record WhenStmt(
        Expression condition,
        Statement thenBranch,
        Optional<Statement> elseBranch) implements Statement {}
