package ai.getfundflow.dsl.ast;

import java.time.LocalDate;

public sealed interface DateExpr permits DateExpr.Literal, DateExpr.Inception, DateExpr.Phrasal {

    record Literal(LocalDate value) implements DateExpr {}

    record Inception() implements DateExpr {
        public static final Inception INSTANCE = new Inception();
    }

    record Phrasal(QualifiedRef ref) implements DateExpr {}
}
