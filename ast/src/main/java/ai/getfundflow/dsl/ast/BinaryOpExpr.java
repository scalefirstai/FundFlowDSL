package ai.getfundflow.dsl.ast;

public record BinaryOpExpr(BinaryOp op, Expression left, Expression right) implements Expression {

    public enum BinaryOp {
        MUL("*"), DIV("/"),
        ADD("+"), SUB("-"),
        LT("<"), LE("<="), GT(">"), GE(">="), EQ("=="), NEQ("!="),
        AND("and"), OR("or");

        private final String symbol;

        BinaryOp(String symbol) {
            this.symbol = symbol;
        }

        public String symbol() {
            return symbol;
        }
    }
}
