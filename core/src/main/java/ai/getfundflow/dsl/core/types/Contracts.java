package ai.getfundflow.dsl.core.types;

public record Contracts() implements Unit {

    public static final Contracts INSTANCE = new Contracts();

    @Override
    public String label() {
        return "contracts";
    }
}
