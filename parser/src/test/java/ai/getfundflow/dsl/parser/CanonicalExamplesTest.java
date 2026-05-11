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
import ai.getfundflow.dsl.semantic.Diagnostic;
import ai.getfundflow.dsl.semantic.DiagnosticCode;
import ai.getfundflow.dsl.semantic.SemanticAnalyzer;
import ai.getfundflow.dsl.semantic.Severity;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CanonicalExamplesTest {

    private static final Currency USD = Currency.getInstance("USD");
    private static final Set<DiagnosticCode> NON_FATAL = Set.of(
            DiagnosticCode.DEFERRED_REFERENCE,
            DiagnosticCode.UNRESOLVED_BINDING,
            DiagnosticCode.LARGE_SCALAR_ON_MONEY);

    private static MoneyVal usd(String amount) {
        return new MoneyVal(Money.of(new BigDecimal(amount.replace(",", "")), USD));
    }

    private static NumberVal num(String value) {
        return new NumberVal(new BigDecimal(value));
    }

    private static Program load(String filename) throws IOException {
        Path file = Paths.get("..", "examples", filename);
        String source = Files.readString(file);
        FundFlowParser.ProgramContext ctx = (FundFlowParser.ProgramContext) ParseHarness.parse(source);
        return new AstBuilder().build(ctx);
    }

    private void assertSemanticAcceptable(Program program) {
        SemanticAnalyzer.SemanticResult result = new SemanticAnalyzer().analyze(program);
        for (Diagnostic d : result.diagnostics().all()) {
            if (d.severity() == Severity.ERROR && !NON_FATAL.contains(d.code())) {
                throw new AssertionError("unexpected semantic error: " + d.render());
            }
        }
    }

    private EvaluationContext ctx(LocalDate asOf, Map<String, RuntimeValue> data) {
        return new EvaluationContext(
                new BusinessDate(asOf, WeekendOnlyCalendar.DEFAULT),
                MapDataSource.of(data),
                WeekendOnlyCalendar.DEFAULT,
                AuditSink.discarding());
    }

    // ========================================================================
    // §9.1 Management fee
    // ========================================================================

    @Test
    void managementFeeAccrualParsesAndEvaluates() throws IOException {
        Program program = load("01_management_fee.ff");
        assertSemanticAcceptable(program);

        Map<String, RuntimeValue> fixture = Map.of(
                "opening nav of share class", usd("10000000"));

        EvaluationResult r = new Evaluator().evaluate(
                program, ctx(LocalDate.of(2026, 3, 15), fixture));

        // 10,000,000 * 0.015 / 365 ≈ 410.96
        assertThat(r.postings()).hasSize(1);
        LedgerEntry entry = r.postings().get(0);
        assertThat(entry.amount().amount()).isEqualByComparingTo("410.96");
        assertThat(entry.account()).contains("Management Fee Payable");
        assertThat(entry.narrative()).contains("Daily mgmt fee accrual");
    }

    // ========================================================================
    // §9.2 Performance fee
    // ========================================================================

    @Test
    void performanceFeeParsesAndEvaluates() throws IOException {
        Program program = load("02_performance_fee.ff");
        assertSemanticAcceptable(program);

        Map<String, RuntimeValue> fixture = Map.of(
                "nav at start of period", usd("100000000"),
                "nav at end of period",   usd("115000000"),
                "high water mark",        usd("105000000"));

        EvaluationResult r = new Evaluator().evaluate(
                program, ctx(LocalDate.of(2026, 12, 31), fixture));

        // gross_return = 15M, hurdle_amount = 100M*8% = 8M, excess = 7M,
        // above_hwm = 10M; fee = 20% * min(7M, 10M) = 1.4M
        assertThat(r.postings()).hasSize(1);
        assertThat(r.postings().get(0).account()).contains("Performance Fee Payable");
        assertThat(r.postings().get(0).amount().amount())
                .isEqualByComparingTo("1400000.00");
    }

    @Test
    void performanceFeeBelowHwmEmitsNoPosting() throws IOException {
        Program program = load("02_performance_fee.ff");
        Map<String, RuntimeValue> fixture = Map.of(
                "nav at start of period", usd("100000000"),
                "nav at end of period",   usd("99000000"),
                "high water mark",        usd("105000000"));

        EvaluationResult r = new Evaluator().evaluate(
                program, ctx(LocalDate.of(2026, 12, 31), fixture));

        assertThat(r.postings()).isEmpty();
    }

    // ========================================================================
    // §9.3 Capital call
    // ========================================================================

    @Test
    void capitalCallParsesAndAllocatesProRata() throws IOException {
        Program program = load("03_capital_call.ff");
        assertSemanticAcceptable(program);

        Map<String, RuntimeValue> fixture = Map.of(
                "investors of fund", new ListVal(List.of(
                        num("100000000"),
                        num("250000000"),
                        num("150000000"))));

        EvaluationResult r = new Evaluator().evaluate(
                program, ctx(LocalDate.of(2026, 6, 30), fixture));

        // 50M call against weights 100/250/150 (sum 500): 10M, 25M, 15M
        assertThat(r.postings()).hasSize(3);
        assertThat(r.postings().get(0).amount().amount()).isEqualByComparingTo("10000000.00");
        assertThat(r.postings().get(1).amount().amount()).isEqualByComparingTo("25000000.00");
        assertThat(r.postings().get(2).amount().amount()).isEqualByComparingTo("15000000.00");

        BigDecimal sum = r.postings().stream()
                .map(e -> e.amount().amount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sum).isEqualByComparingTo("50000000.00");
    }

    // ========================================================================
    // §9.4 NAV calculation
    // ========================================================================

    @Test
    void navCalculationParsesAndPublishes() throws IOException {
        Program program = load("04_nav_calculation.ff");
        assertSemanticAcceptable(program);

        Map<String, RuntimeValue> fixture = Map.of(
                "position market value as of valuation date",
                new ListVal(List.of(usd("95000000"), usd("12000000"), usd("3000000"))),
                "accrued expenses + payables as of valuation date",
                new ListVal(List.of(usd("250000"), usd("750000"))),
                "units outstanding as of valuation date",
                num("950000"),
                "nav as of valuation date",
                usd("110000000"));

        EvaluationResult r = new Evaluator().evaluate(
                program, ctx(LocalDate.of(2026, 3, 31), fixture));

        // The publish statement keys by '<rule>:<expression>' — the expression
        // is the phrasal `nav as of valuation date`.
        Object published = r.outputs().get("Daily NAV Strike:nav as of valuation date");
        assertThat(published).isInstanceOf(MoneyVal.class);
        assertThat(((MoneyVal) published).value().amount())
                .isEqualByComparingTo("110000000.00");
    }

    // ========================================================================
    // §9.5 Series equalization
    // ========================================================================

    @Test
    void seriesEqualizationOnlyChargesSeriesAboveHwm() throws IOException {
        Program program = load("05_equalization.ff");
        assertSemanticAcceptable(program);

        Map<String, RuntimeValue> fixture = Map.of(
                "current nav of series \"A\"",      usd("12000000"),
                "side pocket nav of series \"A\"",  usd("1000000"),
                "high water mark of series \"A\"",  usd("10000000"),
                "current nav of series \"B\"",      usd("9500000"),
                "side pocket nav of series \"B\"",  usd("0"),
                "high water mark of series \"B\"",  usd("10000000"));

        EvaluationResult r = new Evaluator().evaluate(
                program, ctx(LocalDate.of(2026, 12, 31), fixture));

        // Series A: perf_nav 11M − HWM 10M = 1M; 20% × 1M = 200k
        // Series B: perf_nav 9.5M < HWM 10M → 0 → when-guard skips
        assertThat(r.postings()).hasSize(1);
        assertThat(r.postings().get(0).account()).contains("Series A");
        assertThat(r.postings().get(0).amount().amount()).isEqualByComparingTo("200000.00");
    }

    // ========================================================================
    // §9.6 European waterfall
    // ========================================================================

    @Test
    void europeanWaterfallSplitsAcrossAllFourTiers() throws IOException {
        Program program = load("06_european_waterfall.ff");
        assertSemanticAcceptable(program);

        Map<String, RuntimeValue> fixture = Map.of(
                "total realized proceeds for period", usd("200000000"),
                "total drawn capital",                 usd("100000000"));

        EvaluationResult r = new Evaluator().evaluate(
                program, ctx(LocalDate.of(2026, 12, 31), fixture));

        // Expected tiers: 100M ROC + 8M pref + 2M catch-up + 72M LP + 18M GP = 200M
        assertThat(r.postings()).hasSize(5);

        Map<String, BigDecimal> byAccount = new java.util.HashMap<>();
        for (LedgerEntry e : r.postings()) {
            byAccount.merge(e.account(), e.amount().amount(), BigDecimal::add);
        }

        assertThat(byAccount).containsKeys(
                "ledger account \"LP — Return of Capital\"",
                "ledger account \"LP — Preferred Return\"",
                "ledger account \"GP — Catch-up\"",
                "ledger account \"LP — Carried Profit Share\"",
                "ledger account \"GP — Carried Interest\"");

        assertThat(byAccount.get("ledger account \"LP — Return of Capital\""))
                .isEqualByComparingTo("100000000.00");
        assertThat(byAccount.get("ledger account \"LP — Preferred Return\""))
                .isEqualByComparingTo("8000000.00");
        assertThat(byAccount.get("ledger account \"GP — Catch-up\""))
                .isEqualByComparingTo("2000000.00");
        assertThat(byAccount.get("ledger account \"LP — Carried Profit Share\""))
                .isEqualByComparingTo("72000000.00");
        assertThat(byAccount.get("ledger account \"GP — Carried Interest\""))
                .isEqualByComparingTo("18000000.00");

        BigDecimal grandTotal = byAccount.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(grandTotal).isEqualByComparingTo("200000000.00");
    }
}
