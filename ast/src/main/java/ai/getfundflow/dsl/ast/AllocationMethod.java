package ai.getfundflow.dsl.ast;

public sealed interface AllocationMethod permits AllocationMethod.ProRata, AllocationMethod.Equally {

    record ProRata(Expression weight) implements AllocationMethod {}

    record Equally() implements AllocationMethod {
        public static final Equally INSTANCE = new Equally();
    }
}
