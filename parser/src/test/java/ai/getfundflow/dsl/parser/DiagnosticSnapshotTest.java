package ai.getfundflow.dsl.parser;

import static org.assertj.core.api.Assertions.assertThat;

import ai.getfundflow.dsl.ast.Program;
import ai.getfundflow.dsl.diagnostics.DiagnosticRenderer;
import ai.getfundflow.dsl.parser.gen.FundFlowParser;
import ai.getfundflow.dsl.semantic.Diagnostic;
import ai.getfundflow.dsl.semantic.DiagnosticCode;
import ai.getfundflow.dsl.semantic.SemanticAnalyzer;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Snapshot tests for the rust-style diagnostic renderer (spec §10.1).
 * Each test constructs a deliberately-broken program, runs semantic analysis,
 * and asserts that the rendered output contains the expected code, location,
 * and underline.
 */
class DiagnosticSnapshotTest {

    private SemanticAnalyzer.SemanticResult analyze(String source) {
        FundFlowParser.ProgramContext ctx = (FundFlowParser.ProgramContext) ParseHarness.parse(source);
        AstBuilder builder = new AstBuilder();
        Program program = builder.build(ctx, "test.ff");
        return new SemanticAnalyzer().analyze(program, builder.sourceMap());
    }

    private Optional<Diagnostic> firstWith(SemanticAnalyzer.SemanticResult r, DiagnosticCode code) {
        return r.diagnostics().all().stream().filter(d -> d.code() == code).findFirst();
    }

    @Test
    void duplicateDeclarationRenders() {
        String src = """
                rule "First" {
                  description: "..."
                }

                rule "First" {
                  description: "..."
                }
                """;
        SemanticAnalyzer.SemanticResult r = analyze(src);
        Diagnostic d = firstWith(r, DiagnosticCode.DUPLICATE_DECLARATION).orElseThrow();
        String rendered = DiagnosticRenderer.render(d, src);

        assertThat(rendered).contains("error[FF2001]");
        assertThat(rendered).contains("duplicate declaration: 'First'");
        assertThat(rendered).contains("test.ff:5:");
        assertThat(rendered).contains("rule \"First\"");
        assertThat(rendered).contains("^");
    }

    @Test
    void unresolvedBindingRendersWithDidYouMean() {
        String src = """
                rule "Compute" {
                  let total_fee = USD 100
                  let other = totel_fee
                }
                """;
        SemanticAnalyzer.SemanticResult r = analyze(src);
        Diagnostic d = firstWith(r, DiagnosticCode.UNRESOLVED_BINDING).orElseThrow();
        String rendered = DiagnosticRenderer.render(d, src);

        assertThat(rendered).contains("error[FF2002]");
        assertThat(rendered).contains("unresolved binding 'totel_fee'");
        assertThat(rendered).contains("did you mean 'total_fee'?");
        assertThat(rendered).contains("test.ff:3:");
    }

    @Test
    void unknownFunctionRenders() {
        String src = """
                rule "Bad Function" {
                  let x = explode(1, 2, 3)
                }
                """;
        SemanticAnalyzer.SemanticResult r = analyze(src);
        Diagnostic d = firstWith(r, DiagnosticCode.UNKNOWN_FUNCTION).orElseThrow();
        String rendered = DiagnosticRenderer.render(d, src);

        assertThat(rendered).contains("error[FF2003]");
        assertThat(rendered).contains("unknown function: 'explode'");
        assertThat(rendered).contains("test.ff:2:");
    }

    @Test
    void functionArityMismatchRenders() {
        String src = """
                rule "Wrong Arity" {
                  let x = abs(1, 2)
                }
                """;
        SemanticAnalyzer.SemanticResult r = analyze(src);
        Diagnostic d = firstWith(r, DiagnosticCode.FUNCTION_ARITY_MISMATCH).orElseThrow();
        String rendered = DiagnosticRenderer.render(d, src);

        assertThat(rendered).contains("error[FF2004]");
        assertThat(rendered).contains("function 'abs' takes 1 arguments");
    }

    @Test
    void currencyMismatchRenders() {
        String src = """
                rule "Mismatched" {
                  let total = USD 100 + EUR 50
                }
                """;
        SemanticAnalyzer.SemanticResult r = analyze(src);
        Diagnostic d = firstWith(r, DiagnosticCode.CURRENCY_MISMATCH).orElseThrow();
        String rendered = DiagnosticRenderer.render(d, src);

        assertThat(rendered).contains("error[FF1042]");
        assertThat(rendered).contains("currency mismatch in addition");
        assertThat(rendered).contains("Money(USD)");
        assertThat(rendered).contains("Money(EUR)");
    }

    @Test
    void moneyTimesMoneyRenders() {
        String src = """
                rule "Bad Mul" {
                  let x = USD 100 * USD 50
                }
                """;
        SemanticAnalyzer.SemanticResult r = analyze(src);
        Diagnostic d = firstWith(r, DiagnosticCode.MONEY_MULTIPLY_MONEY).orElseThrow();
        String rendered = DiagnosticRenderer.render(d, src);

        assertThat(rendered).contains("error[FF1043]");
        assertThat(rendered).contains("Money * Money is not allowed");
    }

    @Test
    void rendererHandlesUnknownLocationGracefully() {
        Diagnostic d = Diagnostic.error(
                DiagnosticCode.TYPE_MISMATCH,
                ai.getfundflow.dsl.semantic.SourceLocation.UNKNOWN,
                "synthetic example");
        String rendered = DiagnosticRenderer.render(d, "");
        assertThat(rendered).contains("error[FF1001]");
        assertThat(rendered).contains("synthetic example");
        // No source-line context when location is UNKNOWN
        assertThat(rendered).doesNotContain("^");
    }

    @Test
    void renderAllJoinsMultipleDiagnostics() {
        String src = """
                rule "Many Errors" {
                  let a = unknown_one
                  let b = unknown_two
                }
                """;
        SemanticAnalyzer.SemanticResult r = analyze(src);
        List<Diagnostic> errors = r.diagnostics().errors();
        String rendered = DiagnosticRenderer.renderAll(errors, src);
        assertThat(rendered).contains("unknown_one");
        assertThat(rendered).contains("unknown_two");
    }
}
