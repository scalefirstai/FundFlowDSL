package ai.getfundflow.dsl.core.types;

public record Shares() implements Unit {

    public static final Shares INSTANCE = new Shares();

    @Override
    public String label() {
        return "shares";
    }
}
