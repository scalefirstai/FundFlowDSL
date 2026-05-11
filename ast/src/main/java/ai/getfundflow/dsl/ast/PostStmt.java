package ai.getfundflow.dsl.ast;

import java.util.Optional;

public record PostStmt(
        Optional<Expression> subject,
        QualifiedRef target,
        Optional<String> narrative) implements Statement {}
