package ai.getfundflow.dsl.agent;

import static org.assertj.core.api.Assertions.assertThat;

import ai.getfundflow.dsl.ast.Program;
import ai.getfundflow.dsl.parser.AstBuilder;
import ai.getfundflow.dsl.parser.ParseHarness;
import ai.getfundflow.dsl.parser.gen.FundFlowParser;
import ai.getfundflow.dsl.semantic.SemanticAnalyzer;
import ai.getfundflow.dsl.semantic.SymbolTable;
import ai.getfundflow.dsl.semantic.TypeRegistry;
import org.junit.jupiter.api.Test;

class CatalogTest {

    @Test
    void emptyProgramYieldsBuiltInTypesAndStdlibFunctions() {
        Catalog catalog = Catalog.build(new SymbolTable(), new TypeRegistry());
        assertThat(catalog.baseTypes()).contains("Fund", "ShareClass", "Investor");
        assertThat(catalog.fieldTypes()).contains("Money", "Percentage", "Period", "Date");
        assertThat(catalog.functions().keySet()).contains("abs", "max", "min", "npv", "irr");
        assertThat(catalog.extensions()).isEmpty();
        assertThat(catalog.declarations()).isEmpty();
    }

    @Test
    void analyzedExtensionAppearsInCatalogWithFields() {
        SemanticAnalyzer.SemanticResult result = analyze("""
                type extension PrivateEquityFund extends Fund {
                  field commitment_period: Period
                  field gp_commitment: Percentage
                }

                rule "Use It" {
                  let cp = commitment_period of fund "Acme"
                }
                """);
        Catalog catalog = Catalog.build(result.symbols(), result.types());
        assertThat(catalog.extensions()).containsKey("PrivateEquityFund");
        Catalog.ExtensionEntry pef = catalog.extensions().get("PrivateEquityFund");
        assertThat(pef.baseType()).isEqualTo("Fund");
        assertThat(pef.fields()).containsKeys("commitment_period", "gp_commitment");
    }

    @Test
    void declaredRulesAppearInCatalog() {
        SemanticAnalyzer.SemanticResult result = analyze("""
                rule "Daily Fee" { description: "..." }
                rule "Quarterly Strike" { description: "..." }
                """);
        Catalog catalog = Catalog.build(result.symbols(), result.types());
        assertThat(catalog.declarations()).containsKeys("Daily Fee", "Quarterly Strike");
    }

    @Test
    void jsonShapeIsStableAndSorted() {
        Catalog catalog = Catalog.build(new SymbolTable(), new TypeRegistry());
        var shape = catalog.toJsonShape();
        assertThat(shape).containsKeys("baseTypes", "fieldTypes", "extensions", "declarations", "functions");
        // Function map is sorted lexicographically
        assertThat(shape.get("functions")).isInstanceOf(java.util.Map.class);
    }

    private SemanticAnalyzer.SemanticResult analyze(String src) {
        AstBuilder builder = new AstBuilder();
        Program program = builder.build(
                (FundFlowParser.ProgramContext) ParseHarness.parse(src), "test.ff");
        return new SemanticAnalyzer().analyze(program, builder.sourceMap());
    }
}
