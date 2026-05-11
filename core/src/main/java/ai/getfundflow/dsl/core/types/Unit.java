package ai.getfundflow.dsl.core.types;

public sealed interface Unit
        permits Shares, Units, Contracts, Custom {

    String label();
}
