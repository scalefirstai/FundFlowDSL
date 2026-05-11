package ai.getfundflow.dsl.parser;

import static org.assertj.core.api.Assertions.assertThat;

import ai.getfundflow.dsl.ast.Program;
import ai.getfundflow.dsl.parser.gen.FundFlowParser;
import ai.getfundflow.dsl.semantic.Diagnostic;
import ai.getfundflow.dsl.semantic.DiagnosticCode;
import ai.getfundflow.dsl.semantic.SemanticAnalyzer;
import ai.getfundflow.dsl.semantic.Severity;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SemanticIntegrationTest {

    private static final Set<DiagnosticCode> ALLOWED_NON_FATAL = Set.of(
            DiagnosticCode.DEFERRED_REFERENCE,
            DiagnosticCode.UNRESOLVED_BINDING,
            DiagnosticCode.LARGE_SCALAR_ON_MONEY);

    private SemanticAnalyzer.SemanticResult analyzeCanonical(String filename) throws IOException {
        Path corpus = Paths.get("src/test/resources/corpus/valid", filename);
        String source = Files.readString(corpus);
        FundFlowParser.ProgramContext parse = (FundFlowParser.ProgramContext) ParseHarness.parse(source);
        Program program = new AstBuilder().build(parse);
        return new SemanticAnalyzer().analyze(program);
    }

    @Test
    void mgmtFeeRuleAnalyzesCleanly() throws IOException {
        SemanticAnalyzer.SemanticResult r = analyzeCanonical("01_management_fee_accrual.ff");
        assertNoUnexpectedErrors(r);
    }

    @Test
    void perfFeeRuleAnalyzesCleanly() throws IOException {
        SemanticAnalyzer.SemanticResult r = analyzeCanonical("02_performance_fee.ff");
        assertNoUnexpectedErrors(r);
    }

    @Test
    void capitalCallRuleAnalyzesCleanly() throws IOException {
        SemanticAnalyzer.SemanticResult r = analyzeCanonical("03_capital_call_allocation.ff");
        assertNoUnexpectedErrors(r);
    }

    @Test
    void navRuleAnalyzesCleanly() throws IOException {
        SemanticAnalyzer.SemanticResult r = analyzeCanonical("04_nav_calculation.ff");
        assertNoUnexpectedErrors(r);
    }

    @Test
    void rulesAreRegisteredInSymbolTable() throws IOException {
        SemanticAnalyzer.SemanticResult r = analyzeCanonical("01_management_fee_accrual.ff");
        assertThat(r.symbols().lookup("Management Fee Daily Accrual")).isPresent();
    }

    private void assertNoUnexpectedErrors(SemanticAnalyzer.SemanticResult r) {
        for (Diagnostic d : r.diagnostics().all()) {
            if (d.severity() == Severity.ERROR && !ALLOWED_NON_FATAL.contains(d.code())) {
                throw new AssertionError("unexpected error: " + d.render());
            }
        }
    }
}
