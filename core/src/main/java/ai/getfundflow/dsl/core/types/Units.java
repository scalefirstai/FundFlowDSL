package ai.getfundflow.dsl.core.types;

public record Units() implements Unit {

    public static final Units INSTANCE = new Units();

    @Override
    public String label() {
        return "units";
    }
}
