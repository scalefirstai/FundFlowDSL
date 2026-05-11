package ai.getfundflow.dsl.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import ai.getfundflow.dsl.ast.AggregationCall;
import ai.getfundflow.dsl.ast.AsOfExpr;
import ai.getfundflow.dsl.ast.DateExpr;
import ai.getfundflow.dsl.ast.DayCountExpr;
import ai.getfundflow.dsl.ast.DistributeStmt;
import ai.getfundflow.dsl.ast.LetBinding;
import ai.getfundflow.dsl.ast.Literal;
import ai.getfundflow.dsl.ast.NameRef;
import ai.getfundflow.dsl.ast.NounPhrase;
import ai.getfundflow.dsl.ast.NounPhrase.NounAtom;
import ai.getfundflow.dsl.ast.OverExpr;
import ai.getfundflow.dsl.ast.PeriodExpr;
import ai.getfundflow.dsl.ast.PostStmt;
import ai.getfundflow.dsl.ast.Program;
import ai.getfundflow.dsl.ast.PublishStmt;
import ai.getfundflow.dsl.ast.QualifiedRef;
import ai.getfundflow.dsl.ast.RuleClause;
import ai.getfundflow.dsl.ast.RuleDecl;
import ai.getfundflow.dsl.ast.TopLevelDecl;
import ai.getfundflow.dsl.ast.WaterfallDecl;
import ai.getfundflow.dsl.core.calendar.WeekendOnlyCalendar;
import ai.getfundflow.dsl.core.types.Actual365;
import ai.getfundflow.dsl.core.types.BusinessDate;
import ai.getfundflow.dsl.core.types.Money;
import ai.getfundflow.dsl.core.types.Percentage;
import ai.getfundflow.dsl.runtime.RuntimeValue.ListVal;
import ai.getfundflow.dsl.runtime.RuntimeValue.MoneyVal;
import ai.getfundflow.dsl.runtime.RuntimeValue.NumberVal;
import ai.getfundflow.dsl.runtime.RuntimeValue.PercentVal;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class OperatorsTest {

    private static final Currency USD = Currency.getInstance("USD");

    private static QualifiedRef ref(String... atoms) {
        return new QualifiedRef(List.of(new NounPhrase(
                java.util.Arrays.stream(atoms)
                        .map(s -> (NounAtom) new NounAtom.Ident(s))
                        .toList())));
    }

    private static QualifiedRef qrefAccount(String... words) {
        java.util.List<NounAtom> atoms = new java.util.ArrayList<>();
        for (int i = 0; i < words.length - 1; i++) atoms.add(new NounAtom.Ident(words[i]));
        atoms.add(new NounAtom.Quoted(words[words.length - 1]));
        return new QualifiedRef(List.of(new NounPhrase(atoms)));
    }

    private EvaluationContext ctx(LocalDate asOf, Map<String, RuntimeValue> data) {
        return new EvaluationContext(
                new BusinessDate(asOf, WeekendOnlyCalendar.DEFAULT),
                MapDataSource.of(data),
                WeekendOnlyCalendar.DEFAULT,
                AuditSink.discarding());
    }

    // ---- as-of -------------------------------------------------------------

    @Test
    void asOfShiftsLookupDateForPhrasalReferences() {
        // The DataSource returns different prices when queried with different as-of dates.
        DataSource priceTimeMachine = new DataSource() {
            @Override
            public Optional<RuntimeValue> lookup(String name) {
                return Optional.empty();
            }

            @Override
            public Optional<RuntimeValue> lookupAsOf(String name, LocalDate asOf) {
                if (!"share price".equals(name)) return Optional.empty();
                if (asOf.equals(LocalDate.of(2026, 3, 31))) {
                    return Optional.of(new MoneyVal(Money.of(new BigDecimal("100"), USD)));
                }
                if (asOf.equals(LocalDate.of(2026, 6, 30))) {
                    return Optional.of(new MoneyVal(Money.of(new BigDecimal("110"), USD)));
                }
                return Optional.empty();
            }
        };

        Program p = new Program(Optional.empty(), List.of(), List.<TopLevelDecl>of(
                new RuleDecl("Snapshot", List.<RuleClause>of(
                        new LetBinding("q1_price",
                                new AsOfExpr(
                                        new NameRef(ref("share", "price")),
                                        new DateExpr.Literal(LocalDate.of(2026, 3, 31)))),
                        new LetBinding("q2_price",
                                new AsOfExpr(
                                        new NameRef(ref("share", "price")),
                                        new DateExpr.Literal(LocalDate.of(2026, 6, 30)))),
                        new PublishStmt(new NameRef(ref("q1_price")), Optional.empty()),
                        new PublishStmt(new NameRef(ref("q2_price")), Optional.empty())))));

        EvaluationContext c = new EvaluationContext(
                new BusinessDate(LocalDate.of(2026, 1, 1), WeekendOnlyCalendar.DEFAULT),
                priceTimeMachine,
                WeekendOnlyCalendar.DEFAULT,
                AuditSink.discarding());

        EvaluationResult r = new Evaluator().evaluate(p, c);

        assertThat(((MoneyVal) r.outputs().get("Snapshot:q1_price")).value().amount())
                .isEqualByComparingTo("100.00");
        assertThat(((MoneyVal) r.outputs().get("Snapshot:q2_price")).value().amount())
                .isEqualByComparingTo("110.00");
    }

    @Test
    void asOfFallsBackWhenDateUnresolved() {
        Program p = new Program(Optional.empty(), List.of(), List.<TopLevelDecl>of(
                new RuleDecl("Fallback", List.<RuleClause>of(
                        new LetBinding("v",
                                new AsOfExpr(
                                        new Literal.MoneyLit(Money.of(new BigDecimal("42"), USD)),
                                        new DateExpr.Literal(LocalDate.of(2026, 1, 1)))),
                        new PublishStmt(new NameRef(ref("v")), Optional.empty())))));

        EvaluationResult r = new Evaluator().evaluate(p, ctx(LocalDate.of(2026, 6, 1), Map.of()));
        assertThat(((MoneyVal) r.outputs().get("Fallback:v")).value().amount())
                .isEqualByComparingTo("42.00");
    }

    @Test
    void asOfDoesNotMutateCallerContext() {
        Program p = new Program(Optional.empty(), List.of(), List.<TopLevelDecl>of(
                new RuleDecl("Restore", List.<RuleClause>of(
                        new LetBinding("shifted",
                                new AsOfExpr(
                                        new Literal.MoneyLit(Money.of(BigDecimal.ONE, USD)),
                                        new DateExpr.Literal(LocalDate.of(2099, 1, 1)))),
                        // Subsequent post should use the original asOf (not shifted)
                        new PostStmt(
                                Optional.of(new Literal.MoneyLit(Money.of(BigDecimal.TEN, USD))),
                                qrefAccount("ledger", "account", "Test"),
                                Optional.empty())))));

        EvaluationResult r = new Evaluator().evaluate(p, ctx(LocalDate.of(2026, 3, 15), Map.of()));
        assertThat(r.postings()).hasSize(1);
        assertThat(r.postings().get(0).date()).isEqualTo(LocalDate.of(2026, 3, 15));
    }

    // ---- over … using ----------------------------------------------------

    @Test
    void overUsingScalesPercentageByYearFraction() {
        // 8% per annum over Q1 2026 (90 days) using actual/365 → 8% * 90/365 ≈ 1.973%
        Program p = new Program(Optional.empty(), List.of(), List.<TopLevelDecl>of(
                new RuleDecl("Hurdle", List.<RuleClause>of(
                        new LetBinding("r",
                                new OverExpr(
                                        new Literal.PercentLit(Percentage.ofPercent("8")),
                                        new PeriodExpr.ExplicitFromTo(
                                                new DateExpr.Literal(LocalDate.of(2026, 1, 1)),
                                                Optional.of(new DateExpr.Literal(LocalDate.of(2026, 4, 1)))),
                                        Optional.of(new DayCountExpr.Literal(Actual365.INSTANCE)))),
                        new PublishStmt(new NameRef(ref("r")), Optional.empty())))));

        EvaluationResult result = new Evaluator().evaluate(p, ctx(LocalDate.of(2026, 4, 1), Map.of()));
        PercentVal v = (PercentVal) result.outputs().get("Hurdle:r");
        // 0.08 * 90/365 ≈ 0.01972602
        assertThat(v.value().asRatio().doubleValue())
                .isCloseTo(0.0197260, org.assertj.core.data.Offset.offset(1e-5));
    }

    @Test
    void overUsingScalesMoney() {
        Program p = new Program(Optional.empty(), List.of(), List.<TopLevelDecl>of(
                new RuleDecl("Pro Rated", List.<RuleClause>of(
                        new LetBinding("annual_fee",
                                new Literal.MoneyLit(Money.of(new BigDecimal("100000"), USD))),
                        new LetBinding("partial",
                                new OverExpr(
                                        new NameRef(ref("annual_fee")),
                                        new PeriodExpr.ExplicitFromTo(
                                                new DateExpr.Literal(LocalDate.of(2026, 1, 1)),
                                                Optional.of(new DateExpr.Literal(LocalDate.of(2026, 7, 1)))),
                                        Optional.of(new DayCountExpr.Literal(Actual365.INSTANCE)))),
                        new PublishStmt(new NameRef(ref("partial")), Optional.empty())))));

        EvaluationResult r = new Evaluator().evaluate(p, ctx(LocalDate.of(2026, 7, 1), Map.of()));
        MoneyVal v = (MoneyVal) r.outputs().get("Pro Rated:partial");
        // 100,000 * 181/365 ≈ 49,589.04
        assertThat(v.value().amount()).isEqualByComparingTo("49589.04");
    }

    @Test
    void overWithoutDayCountIsPassThrough() {
        Program p = new Program(Optional.empty(), List.of(), List.<TopLevelDecl>of(
                new RuleDecl("PT", List.<RuleClause>of(
                        new LetBinding("v",
                                new OverExpr(
                                        new Literal.MoneyLit(Money.of(BigDecimal.TEN, USD)),
                                        new PeriodExpr.ExplicitFromTo(
                                                new DateExpr.Literal(LocalDate.of(2026, 1, 1)),
                                                Optional.of(new DateExpr.Literal(LocalDate.of(2026, 12, 31)))),
                                        Optional.empty())),
                        new PublishStmt(new NameRef(ref("v")), Optional.empty())))));

        EvaluationResult r = new Evaluator().evaluate(p, ctx(LocalDate.of(2026, 12, 31), Map.of()));
        MoneyVal v = (MoneyVal) r.outputs().get("PT:v");
        assertThat(v.value().amount()).isEqualByComparingTo("10.00");
    }

    // ---- distribute (waterfall) -------------------------------------------

    @Test
    void distributeRunsWaterfallBody() {
        WaterfallDecl wf = new WaterfallDecl("Linear", List.of(
                new PostStmt(
                        Optional.of(new NameRef(ref("distributable"))),
                        qrefAccount("ledger", "account", "GP Distribution"),
                        Optional.empty())));

        RuleDecl rule = new RuleDecl("Pay GP", List.<RuleClause>of(
                new LetBinding("gross", new Literal.MoneyLit(Money.of(new BigDecimal("500000"), USD))),
                new DistributeStmt(new NameRef(ref("gross")), "Linear")));

        Program p = new Program(Optional.empty(), List.of(),
                List.<TopLevelDecl>of(rule, wf));

        EvaluationResult r = new Evaluator().evaluate(p, ctx(LocalDate.of(2026, 12, 31), Map.of()));
        assertThat(r.postings()).hasSize(1);
        assertThat(r.postings().get(0).amount().amount()).isEqualByComparingTo("500000.00");
        assertThat(r.postings().get(0).account()).contains("GP Distribution");
    }

    @Test
    void distributeUnknownWaterfallIsSafe() {
        RuleDecl rule = new RuleDecl("Stray", List.<RuleClause>of(
                new DistributeStmt(
                        new Literal.MoneyLit(Money.of(BigDecimal.ONE, USD)),
                        "DoesNotExist")));
        Program p = new Program(Optional.empty(), List.of(), List.<TopLevelDecl>of(rule));

        EvaluationResult r = new Evaluator().evaluate(p, ctx(LocalDate.of(2026, 1, 1), Map.of()));

        assertThat(r.postings()).isEmpty();
        boolean logged = r.trail().entries().stream()
                .anyMatch(e -> e.description().contains("waterfall not found"));
        assertThat(logged).isTrue();
    }

    @Test
    void distributeAuditEntryRecordsAmount() {
        WaterfallDecl wf = new WaterfallDecl("Empty", List.of());
        RuleDecl rule = new RuleDecl("Audit", List.<RuleClause>of(
                new DistributeStmt(
                        new Literal.MoneyLit(Money.of(new BigDecimal("250"), USD)),
                        "Empty")));
        Program p = new Program(Optional.empty(), List.of(),
                List.<TopLevelDecl>of(rule, wf));

        EvaluationResult r = new Evaluator().evaluate(p, ctx(LocalDate.of(2026, 1, 1), Map.of()));
        boolean recorded = r.trail().entries().stream()
                .anyMatch(e -> e.description().equals("distribute through Empty"));
        assertThat(recorded).isTrue();
    }

    // ---- weighted average ------------------------------------------------

    @Test
    void weightedAverageOverPairedLists() {
        Map<String, RuntimeValue> data = Map.of(
                "monthly_returns", new ListVal(List.of(
                        new NumberVal(new BigDecimal("0.02")),
                        new NumberVal(new BigDecimal("0.04")),
                        new NumberVal(new BigDecimal("-0.01")))),
                "monthly_capital", new ListVal(List.of(
                        new NumberVal(new BigDecimal("100")),
                        new NumberVal(new BigDecimal("200")),
                        new NumberVal(new BigDecimal("100")))));

        Program p = new Program(Optional.empty(), List.of(), List.<TopLevelDecl>of(
                new RuleDecl("WA", List.<RuleClause>of(
                        new LetBinding("warr", new AggregationCall.WeightedAverage(
                                new NameRef(ref("monthly_returns")),
                                new NameRef(ref("monthly_capital")))),
                        new PublishStmt(new NameRef(ref("warr")), Optional.empty())))));

        EvaluationResult r = new Evaluator().evaluate(p, ctx(LocalDate.of(2026, 12, 31), data));
        // (0.02*100 + 0.04*200 + (-0.01)*100) / 400 = 9/400 = 0.0225
        NumberVal v = (NumberVal) r.outputs().get("WA:warr");
        assertThat(v.value().doubleValue())
                .isCloseTo(0.0225, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void weightedAverageReturnsNullWhenLengthsMismatch() {
        Map<String, RuntimeValue> data = Map.of(
                "monthly_returns", new ListVal(List.of(new NumberVal(BigDecimal.ONE))),
                "monthly_capital", new ListVal(List.of(
                        new NumberVal(BigDecimal.ONE),
                        new NumberVal(BigDecimal.ONE))));

        Program p = new Program(Optional.empty(), List.of(), List.<TopLevelDecl>of(
                new RuleDecl("Bad WA", List.<RuleClause>of(
                        new LetBinding("warr", new AggregationCall.WeightedAverage(
                                new NameRef(ref("monthly_returns")),
                                new NameRef(ref("monthly_capital")))),
                        new PublishStmt(new NameRef(ref("warr")), Optional.empty())))));

        EvaluationResult r = new Evaluator().evaluate(p, ctx(LocalDate.of(2026, 12, 31), data));
        assertThat(r.outputs().get("Bad WA:warr"))
                .isInstanceOf(RuntimeValue.NullVal.class);
    }

    @Test
    void weightedAverageWithZeroWeightsReturnsNull() {
        Map<String, RuntimeValue> data = Map.of(
                "returns", new ListVal(List.of(
                        new NumberVal(BigDecimal.ONE), new NumberVal(BigDecimal.ONE))),
                "weights", new ListVal(List.of(
                        new NumberVal(BigDecimal.ZERO), new NumberVal(BigDecimal.ZERO))));

        Program p = new Program(Optional.empty(), List.of(), List.<TopLevelDecl>of(
                new RuleDecl("Zero WA", List.<RuleClause>of(
                        new LetBinding("warr", new AggregationCall.WeightedAverage(
                                new NameRef(ref("returns")),
                                new NameRef(ref("weights")))),
                        new PublishStmt(new NameRef(ref("warr")), Optional.empty())))));

        EvaluationResult r = new Evaluator().evaluate(p, ctx(LocalDate.of(2026, 12, 31), data));
        assertThat(r.outputs().get("Zero WA:warr"))
                .isInstanceOf(RuntimeValue.NullVal.class);
    }
}
