package ai.getfundflow.dsl.ast;

import java.util.Optional;

public record PublishStmt(Expression subject, Optional<DateExpr> asOf) implements Statement {}
