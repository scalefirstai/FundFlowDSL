package ai.getfundflow.dsl.agent;

import static org.assertj.core.api.Assertions.assertThat;

import ai.getfundflow.dsl.semantic.DiagnosticCode;
import org.junit.jupiter.api.Test;

class ValidatorTest {

    private final Validator validator = new Validator();

    @Test
    void cleanProgramReturnsEmptyArrays() {
        Validator.ValidationReport r = validator.validate("""
                rule "Clean" {
                  let amount = USD 100
                  let total = amount + amount
                }
                """);
        assertThat(r.parse()).isEmpty();
        assertThat(r.types().stream()
                .filter(d -> d.severity() == ai.getfundflow.dsl.semantic.Severity.ERROR))
                .isEmpty();
        assertThat(r.eval()).isEmpty();
        assertThat(r.ok()).isTrue();
    }

    @Test
    void parseErrorLandsInParseArray() {
        Validator.ValidationReport r = validator.validate("rule \"Bad\" { let }");
        assertThat(r.parse()).isNotEmpty();
        assertThat(r.ok()).isFalse();
    }

    @Test
    void unresolvedBindingLandsInTypesArray() {
        Validator.ValidationReport r = validator.validate("""
                rule "R" {
                  let x = unknown_name
                }
                """);
        assertThat(r.types()).anyMatch(d -> d.code() == DiagnosticCode.UNRESOLVED_BINDING);
    }

    @Test
    void overlappingEffectivePeriodsLandInEvalArray() {
        Validator.ValidationReport r = validator.validate("""
                rule "A" {
                  applies to: fund "Acme"
                  effective: from 2026-01-01 to 2026-12-31
                }

                rule "B" {
                  applies to: fund "Acme"
                  effective: from 2026-06-01 to 2027-06-30
                }
                """);
        assertThat(r.eval()).anyMatch(d -> d.code() == DiagnosticCode.EFFECTIVE_OVERLAP);
    }

    @Test
    void currencyMismatchLandsInTypesArray() {
        Validator.ValidationReport r = validator.validate("""
                rule "R" {
                  let x = USD 100 + EUR 50
                }
                """);
        assertThat(r.types()).anyMatch(d -> d.code() == DiagnosticCode.CURRENCY_MISMATCH);
        assertThat(r.ok()).isFalse();
    }

    @Test
    void validatorRunsUnder50msOnCleanProgram() {
        // Spec §14.3: validate endpoint under 500ms. We're not at scale yet, but a
        // canonical-example-sized program is well under 50ms.
        String source = """
                rule "Mgmt Fee" {
                  description: "1.5% per annum on opening NAV"
                  applies to: all share classes of fund "Acme"
                  effective: from 2026-01-01
                  let rate = 1.5% per annum
                  let basis = opening nav of share class
                  let day_count = actual/365
                  accrue rate on basis using day_count
                  post to ledger account "Mgmt Fee Payable" with narrative "..."
                }
                """;
        // Warm up the parser
        validator.validate(source);
        long t0 = System.nanoTime();
        for (int i = 0; i < 5; i++) validator.validate(source);
        long elapsedMs = (System.nanoTime() - t0) / 5 / 1_000_000;
        assertThat(elapsedMs).as("per-validation ms").isLessThan(50);
    }
}
