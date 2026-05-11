package ai.getfundflow.dsl.parser;

import static org.assertj.core.api.Assertions.assertThat;

import ai.getfundflow.dsl.ast.Program;
import ai.getfundflow.dsl.parser.gen.FundFlowParser;
import ai.getfundflow.dsl.semantic.Diagnostic;
import ai.getfundflow.dsl.semantic.DiagnosticCode;
import ai.getfundflow.dsl.semantic.SemanticAnalyzer;
import ai.getfundflow.dsl.semantic.Severity;
import org.junit.jupiter.api.Test;

class TypeExtensionTest {

    private SemanticAnalyzer.SemanticResult analyze(String src) {
        AstBuilder builder = new AstBuilder();
        FundFlowParser.ProgramContext ctx = (FundFlowParser.ProgramContext) ParseHarness.parse(src);
        Program program = builder.build(ctx, "test.ff");
        return new SemanticAnalyzer().analyze(program, builder.sourceMap());
    }

    @Test
    void extendingFundRegistersFieldsAndResolvesReferences() {
        String src = """
                type extension PrivateEquityFund extends Fund {
                  field commitment_period: Period
                  field investment_period_end: BusinessDate
                  field gp_commitment: Percentage
                }

                rule "Use Extension" {
                  let cp = commitment_period of fund "Acme PE"
                  let ipe = investment_period_end of fund "Acme PE"
                  let gpc = gp_commitment of fund "Acme PE"
                }
                """;
        SemanticAnalyzer.SemanticResult result = analyze(src);

        // No fatal errors
        long errors = result.diagnostics().errors().size();
        assertThat(errors).as("unexpected errors: %s",
                result.diagnostics().errors()).isZero();

        // Extension registered
        assertThat(result.types().extensions()).containsKey("PrivateEquityFund");

        // Each let-binding gets the right inferred type
        var bindings = result.symbols().bindingsFor("Use Extension");
        assertThat(bindings.get("cp").type().describe()).isEqualTo("Period");
        assertThat(bindings.get("ipe").type().describe()).isEqualTo("Date");
        assertThat(bindings.get("gpc").type().describe()).isEqualTo("Percentage");
    }

    @Test
    void unknownBaseTypeEmitsFF1100() {
        String src = """
                type extension MyExtension extends Banana {
                  field a: Period
                }
                """;
        SemanticAnalyzer.SemanticResult result = analyze(src);
        assertThat(result.diagnostics().errors())
                .anyMatch(d -> d.code() == DiagnosticCode.UNKNOWN_BASE_TYPE);
    }

    @Test
    void unknownFieldTypeEmitsFF1102() {
        String src = """
                type extension MyExt extends Fund {
                  field a: Bogus
                }
                """;
        SemanticAnalyzer.SemanticResult result = analyze(src);
        Diagnostic d = result.diagnostics().errors().stream()
                .filter(x -> x.code() == DiagnosticCode.UNKNOWN_FIELD_TYPE)
                .findFirst()
                .orElseThrow();
        assertThat(d.message()).contains("Bogus");
    }

    @Test
    void unknownFieldOnExtendedBaseEmitsFF1101() {
        String src = """
                type extension PEF extends Fund {
                  field commitment_period: Period
                }

                rule "Bad Field" {
                  let x = not_a_field of fund "Acme"
                }
                """;
        SemanticAnalyzer.SemanticResult result = analyze(src);
        assertThat(result.diagnostics().errors())
                .anyMatch(d -> d.code() == DiagnosticCode.UNKNOWN_EXTENSION_FIELD);
    }

    @Test
    void phrasalFieldRefStillDefersWhenNoExtensionsDeclared() {
        // Without ANY extension declared, the field-on-entity heuristic stays out of the way
        // and the original DEFERRED_REFERENCE behaviour applies.
        String src = """
                rule "No Extensions" {
                  let x = some_phrase of fund "Acme"
                }
                """;
        SemanticAnalyzer.SemanticResult result = analyze(src);
        assertThat(result.diagnostics().all())
                .anyMatch(d -> d.code() == DiagnosticCode.DEFERRED_REFERENCE);
        // and no FF1101
        assertThat(result.diagnostics().all())
                .noneMatch(d -> d.code() == DiagnosticCode.UNKNOWN_EXTENSION_FIELD);
    }

    @Test
    void multipleExtensionsOfSameBaseAggregateFields() {
        String src = """
                type extension ExtA extends Fund {
                  field alpha: Money
                }

                type extension ExtB extends Fund {
                  field beta: Percentage
                }

                rule "Combine" {
                  let a = alpha of fund "x"
                  let b = beta of fund "x"
                }
                """;
        SemanticAnalyzer.SemanticResult result = analyze(src);
        assertThat(result.diagnostics().errors()).isEmpty();
        var bindings = result.symbols().bindingsFor("Combine");
        assertThat(bindings.get("a").type().describe()).startsWith("Money");
        assertThat(bindings.get("b").type().describe()).isEqualTo("Percentage");
    }

    @Test
    void extensionFieldsCanFlowIntoArithmetic() {
        String src = """
                type extension PEF extends Fund {
                  field gp_commitment: Percentage
                }

                rule "Arithmetic" {
                  let pct = gp_commitment of fund "x"
                  let scaled = USD 1000000 * pct
                }
                """;
        SemanticAnalyzer.SemanticResult result = analyze(src);

        long fatal = result.diagnostics().all().stream()
                .filter(d -> d.severity() == Severity.ERROR)
                .count();
        assertThat(fatal).isZero();
        var bindings = result.symbols().bindingsFor("Arithmetic");
        assertThat(bindings.get("scaled").type().describe()).startsWith("Money");
    }
}
