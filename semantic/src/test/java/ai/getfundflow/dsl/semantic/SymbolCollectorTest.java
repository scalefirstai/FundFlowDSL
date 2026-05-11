package ai.getfundflow.dsl.semantic;

import static org.assertj.core.api.Assertions.assertThat;

import ai.getfundflow.dsl.ast.Program;
import ai.getfundflow.dsl.ast.RuleDecl;
import ai.getfundflow.dsl.ast.TopLevelDecl;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SymbolCollectorTest {

    @Test
    void collectsRuleNames() {
        Program p = new Program(
                Optional.empty(),
                List.of(),
                List.<TopLevelDecl>of(
                        new RuleDecl("Mgmt Fee", List.of()),
                        new RuleDecl("Perf Fee", List.of())));
        SymbolTable table = new SymbolTable();
        Diagnostics diags = new Diagnostics();
        new SymbolCollector(table, diags).collect(p);

        assertThat(table.declarations()).containsKeys("Mgmt Fee", "Perf Fee");
        assertThat(diags.errors()).isEmpty();
    }

    @Test
    void detectsDuplicateRules() {
        Program p = new Program(
                Optional.empty(),
                List.of(),
                List.<TopLevelDecl>of(
                        new RuleDecl("Same Name", List.of()),
                        new RuleDecl("Same Name", List.of())));
        SymbolTable table = new SymbolTable();
        Diagnostics diags = new Diagnostics();
        new SymbolCollector(table, diags).collect(p);

        assertThat(diags.errors())
                .extracting(d -> d.code())
                .contains(DiagnosticCode.DUPLICATE_DECLARATION);
    }
}
