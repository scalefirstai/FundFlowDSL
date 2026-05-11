package ai.getfundflow.dsl.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import ai.getfundflow.dsl.ast.AccrueStmt;
import ai.getfundflow.dsl.ast.DayCountExpr;
import ai.getfundflow.dsl.ast.LetBinding;
import ai.getfundflow.dsl.ast.Literal;
import ai.getfundflow.dsl.ast.NameRef;
import ai.getfundflow.dsl.ast.NounPhrase;
import ai.getfundflow.dsl.ast.NounPhrase.NounAtom;
import ai.getfundflow.dsl.ast.PostStmt;
import ai.getfundflow.dsl.ast.Program;
import ai.getfundflow.dsl.ast.PublishStmt;
import ai.getfundflow.dsl.ast.QualifiedRef;
import ai.getfundflow.dsl.ast.RuleClause;
import ai.getfundflow.dsl.ast.RuleDecl;
import ai.getfundflow.dsl.ast.TopLevelDecl;
import ai.getfundflow.dsl.core.calendar.WeekendOnlyCalendar;
import ai.getfundflow.dsl.core.types.Actual360;
import ai.getfundflow.dsl.core.types.Actual365;
import ai.getfundflow.dsl.core.types.BusinessDate;
import ai.getfundflow.dsl.core.types.Money;
import ai.getfundflow.dsl.core.types.Percentage;
import ai.getfundflow.dsl.runtime.RuntimeValue.MoneyVal;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AccrueTest {

    private static final Currency USD = Currency.getInstance("USD");

    private static QualifiedRef ref(String atom) {
        return new QualifiedRef(List.of(new NounPhrase(List.of(new NounAtom.Ident(atom)))));
    }

    private static QualifiedRef qrefAccount(String... words) {
        java.util.List<NounAtom> atoms = new java.util.ArrayList<>();
        for (int i = 0; i < words.length - 1; i++) atoms.add(new NounAtom.Ident(words[i]));
        atoms.add(new NounAtom.Quoted(words[words.length - 1]));
        return new QualifiedRef(List.of(new NounPhrase(atoms)));
    }

    private static EvaluationContext ctxAt(LocalDate asOf) {
        return EvaluationContext.minimal(new BusinessDate(asOf, WeekendOnlyCalendar.DEFAULT));
    }

    private static Program accrueProgram(ai.getfundflow.dsl.core.types.DayCount dc) {
        return new Program(Optional.empty(), List.of(), List.<TopLevelDecl>of(
                new RuleDecl("Mgmt Fee", List.<RuleClause>of(
                        new LetBinding("rate", new Literal.PercentLit(Percentage.ofPercent("1.5"))),
                        new LetBinding("basis",
                                new Literal.MoneyLit(Money.of(new BigDecimal("10000000"), USD))),
                        new AccrueStmt(
                                new NameRef(ref("rate")),
                                new NameRef(ref("basis")),
                                new DayCountExpr.Literal(dc)),
                        new PostStmt(
                                Optional.empty(),
                                qrefAccount("ledger", "account", "Mgmt Fee Payable"),
                                Optional.of("Daily mgmt fee accrual"))))));
    }

    @Test
    void accrueWithActual365ProducesOneDayFraction() {
        Program p = accrueProgram(Actual365.INSTANCE);
        EvaluationResult r = new Evaluator().evaluate(p, ctxAt(LocalDate.of(2026, 3, 15)));

        // 10,000,000 * 0.015 * (1/365) ≈ 410.96
        assertThat(r.postings()).hasSize(1);
        BigDecimal posted = r.postings().get(0).amount().amount();
        assertThat(posted).isEqualByComparingTo("410.96");
    }

    @Test
    void accrueWithActual360ProducesDifferentAmount() {
        Program p = accrueProgram(Actual360.INSTANCE);
        EvaluationResult r = new Evaluator().evaluate(p, ctxAt(LocalDate.of(2026, 3, 15)));

        // 10,000,000 * 0.015 * (1/360) ≈ 416.67
        BigDecimal posted = r.postings().get(0).amount().amount();
        assertThat(posted).isEqualByComparingTo("416.67");
    }

    @Test
    void barePostConsumesAccrualRegister() {
        Program p = accrueProgram(Actual365.INSTANCE);
        EvaluationResult r = new Evaluator().evaluate(p, ctxAt(LocalDate.of(2026, 3, 15)));

        // The post statement has no subject; the accrual flows through.
        assertThat(r.postings()).hasSize(1);
        assertThat(r.postings().get(0).account()).contains("Mgmt Fee Payable");
        assertThat(r.postings().get(0).narrative()).contains("Daily mgmt fee accrual");
    }

    @Test
    void accrueRecordsAuditEntry() {
        Program p = accrueProgram(Actual365.INSTANCE);
        EvaluationResult r = new Evaluator().evaluate(p, ctxAt(LocalDate.of(2026, 3, 15)));

        boolean accrueLogged = r.trail().entries().stream()
                .anyMatch(e -> e.description().equals("accrue"));
        assertThat(accrueLogged).isTrue();
    }
}
