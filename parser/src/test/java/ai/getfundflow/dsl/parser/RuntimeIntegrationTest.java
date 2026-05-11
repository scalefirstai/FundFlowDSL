package ai.getfundflow.dsl.parser;

import static org.assertj.core.api.Assertions.assertThat;

import ai.getfundflow.dsl.ast.Program;
import ai.getfundflow.dsl.core.calendar.WeekendOnlyCalendar;
import ai.getfundflow.dsl.core.types.BusinessDate;
import ai.getfundflow.dsl.core.types.Money;
import ai.getfundflow.dsl.parser.gen.FundFlowParser;
import ai.getfundflow.dsl.runtime.AuditSink;
import ai.getfundflow.dsl.runtime.EvaluationContext;
import ai.getfundflow.dsl.runtime.EvaluationResult;
import ai.getfundflow.dsl.runtime.Evaluator;
import ai.getfundflow.dsl.runtime.LedgerEntry;
import ai.getfundflow.dsl.runtime.MapDataSource;
import ai.getfundflow.dsl.runtime.RuntimeValue;
import ai.getfundflow.dsl.runtime.RuntimeValue.ListVal;
import ai.getfundflow.dsl.runtime.RuntimeValue.MoneyVal;
import ai.getfundflow.dsl.runtime.RuntimeValue.NumberVal;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RuntimeIntegrationTest {

    private static final Currency USD = Currency.getInstance("USD");

    private Program parseGolden(String filename) throws IOException {
        Path corpus = Paths.get("src/test/resources/golden", filename);
        String source = Files.readString(corpus);
        FundFlowParser.ProgramContext ctx = (FundFlowParser.ProgramContext) ParseHarness.parse(source);
        return new AstBuilder().build(ctx);
    }

    private EvaluationContext ctx(LocalDate asOf, Map<String, RuntimeValue> data) {
        return new EvaluationContext(
                new BusinessDate(asOf, WeekendOnlyCalendar.DEFAULT),
                MapDataSource.of(data),
                WeekendOnlyCalendar.DEFAULT,
                AuditSink.discarding());
    }

    @Test
    void syntheticMgmtFeeProducesExpectedDailyAmount() throws IOException {
        Program program = parseGolden("synthetic_mgmt_fee.ff");
        Map<String, RuntimeValue> data = Map.of(
                "opening nav", new MoneyVal(Money.of(new BigDecimal("10000000"), USD)));

        EvaluationResult r = new Evaluator().evaluate(
                program, ctx(LocalDate.of(2026, 3, 15), data));

        // 10,000,000 * (0.015 per annum / 365) ≈ 410.96 for one day
        assertThat(r.postings()).hasSize(1);
        LedgerEntry entry = r.postings().get(0);
        assertThat(entry.amount().amount()).isEqualByComparingTo("410.96");
        assertThat(entry.account()).contains("Management Fee Payable");
        assertThat(entry.narrative()).contains("Daily mgmt fee accrual");
    }

    @Test
    void syntheticFullPipelineExercisesEveryOperator() throws IOException {
        Program program = parseGolden("synthetic_full_pipeline.ff");

        Map<String, RuntimeValue> data = Map.of(
                "investor_weights", new ListVal(List.of(
                        new NumberVal(new BigDecimal("100")),
                        new NumberVal(new BigDecimal("300")),
                        new NumberVal(new BigDecimal("600")))));

        EvaluationResult r = new Evaluator().evaluate(
                program, ctx(LocalDate.of(2026, 4, 1), data));

        // Period Mgmt Fee: 120k * 90/365 ≈ 29,589.04
        MoneyVal partial = (MoneyVal) r.outputs().get("Period Mgmt Fee:partial");
        assertThat(partial.value().amount().doubleValue())
                .isCloseTo(29589.04, org.assertj.core.data.Offset.offset(0.01));

        // Allocation: 100k, 300k, 600k → 3 ledger entries to Capital Called Receivable
        long capitalCalls = r.postings().stream()
                .filter(p -> p.account().contains("Capital Called"))
                .count();
        assertThat(capitalCalls).isEqualTo(3);

        BigDecimal callTotal = r.postings().stream()
                .filter(p -> p.account().contains("Capital Called"))
                .map(p -> p.amount().amount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(callTotal).isEqualByComparingTo("1000000.00");

        // Distribute: gross routes through waterfall → posted to GP Carried Interest
        long gpEntries = r.postings().stream()
                .filter(p -> p.account().contains("GP Carried Interest"))
                .count();
        assertThat(gpEntries).isEqualTo(1);

        LedgerEntry gpEntry = r.postings().stream()
                .filter(p -> p.account().contains("GP Carried Interest"))
                .findFirst()
                .orElseThrow();
        assertThat(gpEntry.amount().amount()).isEqualByComparingTo("500000.00");
    }
}
