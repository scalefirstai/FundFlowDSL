package ai.getfundflow.dsl.runtime;

import ai.getfundflow.dsl.ast.Program;

public interface Interpreter {

    EvaluationResult evaluate(Program program, EvaluationContext context);
}
