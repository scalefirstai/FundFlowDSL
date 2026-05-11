package ai.getfundflow.dsl.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import ai.getfundflow.dsl.ast.AllocateStmt;
import ai.getfundflow.dsl.ast.AllocationMethod;
import ai.getfundflow.dsl.ast.LetBinding;
import ai.getfundflow.dsl.ast.Literal;
import ai.getfundflow.dsl.ast.NameRef;
import ai.getfundflow.dsl.ast.NounPhrase;
import ai.getfundflow.dsl.ast.NounPhrase.NounAtom;
import ai.getfundflow.dsl.ast.PostStmt;
import ai.getfundflow.dsl.ast.Program;
import ai.getfundflow.dsl.ast.QualifiedRef;
import ai.getfundflow.dsl.ast.RuleClause;
import ai.getfundflow.dsl.ast.RuleDecl;
import ai.getfundflow.dsl.ast.TopLevelDecl;
import ai.getfundflow.dsl.core.calendar.WeekendOnlyCalendar;
import ai.getfundflow.dsl.core.types.BusinessDate;
import ai.getfundflow.dsl.core.types.Money;
import ai.getfundflow.dsl.runtime.RuntimeValue.ListVal;
import ai.getfundflow.dsl.runtime.RuntimeValue.MoneyVal;
import ai.getfundflow.dsl.runtime.RuntimeValue.NumberVal;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AllocateTest {

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

    private static MoneyVal money(String amount) {
        return new MoneyVal(Money.of(new BigDecimal(amount), USD));
    }

    private EvaluationContext ctxWith(java.util.Map<String, RuntimeValue> data) {
        return new EvaluationContext(
                new BusinessDate(LocalDate.of(2026, 3, 15), WeekendOnlyCalendar.DEFAULT),
                MapDataSource.of(data),
                WeekendOnlyCalendar.DEFAULT,
                AuditSink.discarding());
    }

    private Program allocateProgram(AllocationMethod method) {
        return new Program(Optional.empty(), List.of(), List.<TopLevelDecl>of(
                new RuleDecl("Capital Call", List.<RuleClause>of(
                        new LetBinding("call_amount",
                                new Literal.MoneyLit(Money.of(new BigDecimal("1000000"), USD))),
                        new AllocateStmt(
                                new NameRef(ref("call_amount")),
                                new NameRef(ref("investor", "weights")),
                                method),
                        new PostStmt(
                                Optional.of(new NameRef(ref("each", "allocation"))),
                                qrefAccount("ledger", "account", "Capital Called"),
                                Optional.empty())))));
    }

    @Test
    void proRataDistributesProportionally() {
        java.util.Map<String, RuntimeValue> data = java.util.Map.of(
                "investor weights", new ListVal(List.of(
                        new NumberVal(new BigDecimal("100")),
                        new NumberVal(new BigDecimal("300")),
                        new NumberVal(new BigDecimal("600")))));

        EvaluationResult r = new Evaluator().evaluate(
                allocateProgram(new AllocationMethod.ProRata(new NameRef(ref("ignored")))),
                ctxWith(data));

        assertThat(r.postings()).hasSize(3);
        // 1,000,000 * 0.1, 0.3, 0.6 → 100k, 300k, 600k
        assertThat(r.postings().get(0).amount().amount()).isEqualByComparingTo("100000.00");
        assertThat(r.postings().get(1).amount().amount()).isEqualByComparingTo("300000.00");
        assertThat(r.postings().get(2).amount().amount()).isEqualByComparingTo("600000.00");
    }

    @Test
    void equallyDistributesEvenly() {
        java.util.Map<String, RuntimeValue> data = java.util.Map.of(
                "investor weights", new ListVal(List.of(
                        new NumberVal(BigDecimal.ONE),
                        new NumberVal(BigDecimal.ONE),
                        new NumberVal(BigDecimal.ONE),
                        new NumberVal(BigDecimal.ONE))));

        EvaluationResult r = new Evaluator().evaluate(
                allocateProgram(AllocationMethod.Equally.INSTANCE),
                ctxWith(data));

        assertThat(r.postings()).hasSize(4);
        for (LedgerEntry e : r.postings()) {
            assertThat(e.amount().amount()).isEqualByComparingTo("250000");
        }
    }

    @Test
    void proRataInvariantSumEqualsInputAmount() {
        // Weights chosen to force rounding residuals.
        java.util.Map<String, RuntimeValue> data = java.util.Map.of(
                "investor weights", new ListVal(List.of(
                        new NumberVal(new BigDecimal("1")),
                        new NumberVal(new BigDecimal("1")),
                        new NumberVal(new BigDecimal("1")))));

        EvaluationResult r = new Evaluator().evaluate(
                allocateProgram(new AllocationMethod.ProRata(new NameRef(ref("ignored")))),
                ctxWith(data));

        // 1,000,000 / 3 → 333,333.33 / 333,333.33 / 333,333.34 (last absorbs residual)
        BigDecimal sum = r.postings().stream()
                .map(e -> e.amount().amount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sum).isEqualByComparingTo("1000000.00");
    }

    @Test
    void equallyInvariantSumEqualsInputAmount() {
        java.util.Map<String, RuntimeValue> data = java.util.Map.of(
                "investor weights", new ListVal(List.of(
                        new NumberVal(BigDecimal.ONE),
                        new NumberVal(BigDecimal.ONE),
                        new NumberVal(BigDecimal.ONE),
                        new NumberVal(BigDecimal.ONE),
                        new NumberVal(BigDecimal.ONE),
                        new NumberVal(BigDecimal.ONE),
                        new NumberVal(BigDecimal.ONE))));

        EvaluationResult r = new Evaluator().evaluate(
                allocateProgram(AllocationMethod.Equally.INSTANCE),
                ctxWith(data));

        BigDecimal sum = r.postings().stream()
                .map(e -> e.amount().amount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sum).isEqualByComparingTo("1000000.00");
    }

    @Test
    void zeroWeightsFallBackToEqualSplit() {
        java.util.Map<String, RuntimeValue> data = java.util.Map.of(
                "investor weights", new ListVal(List.of(
                        new NumberVal(BigDecimal.ZERO),
                        new NumberVal(BigDecimal.ZERO))));

        EvaluationResult r = new Evaluator().evaluate(
                allocateProgram(new AllocationMethod.ProRata(new NameRef(ref("ignored")))),
                ctxWith(data));

        assertThat(r.postings()).hasSize(2);
        BigDecimal sum = r.postings().stream()
                .map(e -> e.amount().amount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sum).isEqualByComparingTo("1000000.00");
    }
}
