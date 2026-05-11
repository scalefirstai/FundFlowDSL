package ai.getfundflow.dsl.stdlib;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FunctionRegistryTest {

    @Test
    void mathFunctionsRegistered() {
        assertThat(FunctionRegistry.isKnown("abs")).isTrue();
        assertThat(FunctionRegistry.isKnown("round")).isTrue();
        assertThat(FunctionRegistry.isKnown("max")).isTrue();
        assertThat(FunctionRegistry.isKnown("min")).isTrue();
        assertThat(FunctionRegistry.isKnown("sqrt")).isTrue();
    }

    @Test
    void financialFunctionsRegistered() {
        assertThat(FunctionRegistry.isKnown("npv")).isTrue();
        assertThat(FunctionRegistry.isKnown("irr")).isTrue();
        assertThat(FunctionRegistry.isKnown("xnpv")).isTrue();
        assertThat(FunctionRegistry.isKnown("xirr")).isTrue();
        assertThat(FunctionRegistry.isKnown("pv")).isTrue();
        assertThat(FunctionRegistry.isKnown("fv")).isTrue();
        assertThat(FunctionRegistry.isKnown("pmt")).isTrue();
    }

    @Test
    void dateFunctionsRegistered() {
        assertThat(FunctionRegistry.isKnown("year")).isTrue();
        assertThat(FunctionRegistry.isKnown("eomonth")).isTrue();
        assertThat(FunctionRegistry.isKnown("datediff")).isTrue();
    }

    @Test
    void todayIsForbidden() {
        assertThat(FunctionRegistry.isKnown("today")).isFalse();
        assertThat(FunctionRegistry.isKnown("now")).isFalse();
    }

    @Test
    void unknownReturnsEmpty() {
        assertThat(FunctionRegistry.lookup("not_a_function")).isEmpty();
    }

    @Test
    void signatureCarriesArity() {
        FunctionRegistry.Signature abs = FunctionRegistry.lookup("abs").orElseThrow();
        assertThat(abs.minArity()).isEqualTo(1);
        assertThat(abs.maxArity()).isEqualTo(1);
        assertThat(abs.category()).isEqualTo(FunctionRegistry.Category.MATH);

        FunctionRegistry.Signature max = FunctionRegistry.lookup("max").orElseThrow();
        assertThat(max.minArity()).isEqualTo(1);
        assertThat(max.maxArity()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void registryHasExpectedSize() {
        // Math: 11, Stats: 8, Financial: 7, Date: 6 = 32
        assertThat(FunctionRegistry.names()).hasSize(32);
    }
}
