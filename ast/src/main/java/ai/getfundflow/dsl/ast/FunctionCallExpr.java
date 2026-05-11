package ai.getfundflow.dsl.ast;

import java.util.List;

public record FunctionCallExpr(String name, List<Expression> arguments) implements Expression {
    public FunctionCallExpr {
        arguments = List.copyOf(arguments);
    }
}
