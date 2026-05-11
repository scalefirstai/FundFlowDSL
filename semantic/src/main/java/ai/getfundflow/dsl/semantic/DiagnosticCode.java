package ai.getfundflow.dsl.semantic;

public enum DiagnosticCode {

    // 1xxx — Type errors
    TYPE_MISMATCH("FF1001", Severity.ERROR, "type mismatch"),
    CURRENCY_MISMATCH("FF1042", Severity.ERROR, "currency mismatch"),
    MONEY_MULTIPLY_MONEY("FF1043", Severity.ERROR, "Money * Money is not allowed"),
    UNIT_MISMATCH("FF1044", Severity.ERROR, "unit mismatch"),
    INVALID_OPERAND("FF1050", Severity.ERROR, "invalid operand types for operator"),
    LARGE_SCALAR_ON_MONEY("FF1060", Severity.WARNING,
            "BigDecimal scalar > 1000 multiplied by Money — did you forget %?"),
    UNKNOWN_BASE_TYPE("FF1100", Severity.ERROR,
            "extension extends an unknown base type"),
    UNKNOWN_EXTENSION_FIELD("FF1101", Severity.ERROR,
            "no extension declares this field on the named base type"),
    UNKNOWN_FIELD_TYPE("FF1102", Severity.ERROR,
            "field type is not a known DSL type"),

    // 2xxx — Symbol / name errors
    DUPLICATE_DECLARATION("FF2001", Severity.ERROR, "duplicate declaration"),
    UNRESOLVED_BINDING("FF2002", Severity.ERROR, "unresolved binding"),
    UNKNOWN_FUNCTION("FF2003", Severity.ERROR, "unknown function"),
    FUNCTION_ARITY_MISMATCH("FF2004", Severity.ERROR, "function arity mismatch"),
    FORBIDDEN_FUNCTION("FF4001", Severity.ERROR,
            "this function is forbidden inside the engine (determinism rule)"),

    // 3xxx — Effectivity / scope
    EFFECTIVE_OVERLAP("FF3001", Severity.ERROR,
            "rules with overlapping effective periods conflict in the same scope"),
    INVERTED_PERIOD("FF3002", Severity.ERROR, "period end is before start"),

    // 9xxx — Deferred / info
    DEFERRED_REFERENCE("FF9001", Severity.INFO,
            "phrasal reference deferred — will be resolved against the per-fund domain catalog");

    private final String code;
    private final Severity defaultSeverity;
    private final String summary;

    DiagnosticCode(String code, Severity defaultSeverity, String summary) {
        this.code = code;
        this.defaultSeverity = defaultSeverity;
        this.summary = summary;
    }

    public String code() {
        return code;
    }

    public Severity defaultSeverity() {
        return defaultSeverity;
    }

    public String summary() {
        return summary;
    }
}
