package ai.getfundflow.dsl.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import ai.getfundflow.dsl.ast.BinaryOpExpr;
import ai.getfundflow.dsl.ast.BinaryOpExpr.BinaryOp;
import ai.getfundflow.dsl.ast.Expression;
import ai.getfundflow.dsl.ast.FunctionCallExpr;
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
import ai.getfundflow.dsl.ast.WhenStmt;
import ai.getfundflow.dsl.core.calendar.WeekendOnlyCalendar;
import ai.getfundflow.dsl.core.types.BusinessDate;
import ai.getfundflow.dsl.core.types.Money;
import ai.getfundflow.dsl.core.types.Percentage;
import ai.getfundflow.dsl.runtime.RuntimeValue.MoneyVal;
import ai.getfundflow.dsl.runtime.RuntimeValue.NumberVal;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class EvaluatorTest {

    private static final Currency USD = Currency.getInstance("USD");

    private static EvaluationContext minimalCtx() {
        return EvaluationContext.minimal(
                new BusinessDate(LocalDate.of(2026, 3, 31), WeekendOnlyCalendar.DEFAULT));
    }

    private static QualifiedRef ref(String... atoms) {
        return new QualifiedRef(List.of(new NounPhrase(
                java.util.Arrays.stream(atoms)
                        .map(s -> (NounAtom) new NounAtom.Ident(s))
                        .toList())));
    }

    private static QualifiedRef qrefAccount(String... words) {
        List<NounAtom> atoms = new java.util.ArrayList<>();
        for (int i = 0; i < words.length - 1; i++) atoms.add(new NounAtom.Ident(words[i]));
        atoms.add(new NounAtom.Quoted(words[words.length - 1]));
        return new QualifiedRef(List.of(new NounPhrase(atoms)));
    }

    private static Expression money(String amount) {
        return new Literal.MoneyLit(Money.of(new BigDecimal(amount), USD));
    }

    private static Expression pct(String value) {
        return new Literal.PercentLit(Percentage.ofPercent(value));
    }

    private static Expression num(String value) {
        return new Literal.NumberLit(new BigDecimal(value));
    }

    private static Program program(RuleDecl... rules) {
        return new Program(
                Optional.empty(),
                List.of(),
                java.util.Arrays.stream(rules).map(r -> (TopLevelDecl) r).toList());
    }

    @Test
    void letBindingAndArithmetic() {
        Program p = program(new RuleDecl("Add Money", List.<RuleClause>of(
                new LetBinding("a", money("100")),
                new LetBinding("b", money("50")),
                new LetBinding("total", new BinaryOpExpr(BinaryOp.ADD,
                        new NameRef(ref("a")), new NameRef(ref("b")))),
                new PublishStmt(new NameRef(ref("total")), Optional.empty()))));

        EvaluationResult r = new Evaluator().evaluate(p, minimalCtx());

        assertThat(r.outputs())
                .extractingByKey("Add Money:total")
                .isEqualTo(new MoneyVal(Money.of(new BigDecimal("150"), USD)));
    }

    @Test
    void moneyTimesPercentageProducesMoney() {
        Program p = program(new RuleDecl("Mgmt Fee Inline", List.<RuleClause>of(
                new LetBinding("nav", money("1000000")),
                new LetBinding("rate", pct("1.5")),
                new LetBinding("fee", new BinaryOpExpr(BinaryOp.MUL,
                        new NameRef(ref("nav")), new NameRef(ref("rate")))),
                new PublishStmt(new NameRef(ref("fee")), Optional.empty()))));

        EvaluationResult r = new Evaluator().evaluate(p, minimalCtx());

        MoneyVal fee = (MoneyVal) r.outputs().get("Mgmt Fee Inline:fee");
        assertThat(fee.value().amount()).isEqualByComparingTo("15000.00");
    }

    @Test
    void postEmitsLedgerEntry() {
        Program p = program(new RuleDecl("Post Fee", List.<RuleClause>of(
                new LetBinding("fee", money("250.50")),
                new PostStmt(
                        Optional.of(new NameRef(ref("fee"))),
                        qrefAccount("ledger", "account", "Mgmt Fee Payable"),
                        Optional.of("Daily mgmt fee accrual")))));

        EvaluationResult r = new Evaluator().evaluate(p, minimalCtx());

        assertThat(r.postings()).hasSize(1);
        LedgerEntry entry = r.postings().get(0);
        assertThat(entry.amount().amount()).isEqualByComparingTo("250.50");
        assertThat(entry.narrative()).contains("Daily mgmt fee accrual");
    }

    @Test
    void functionCallMaxOnMoney() {
        Program p = program(new RuleDecl("Cap", List.<RuleClause>of(
                new LetBinding("delta", new BinaryOpExpr(BinaryOp.SUB, money("100"), money("200"))),
                new LetBinding("capped", new FunctionCallExpr("max",
                        List.of(money("0"), new NameRef(ref("delta"))))),
                new PublishStmt(new NameRef(ref("capped")), Optional.empty()))));

        EvaluationResult r = new Evaluator().evaluate(p, minimalCtx());

        MoneyVal capped = (MoneyVal) r.outputs().get("Cap:capped");
        assertThat(capped.value().amount()).isEqualByComparingTo("0");
    }

    @Test
    void whenThenElseDispatchesByCondition() {
        Program p = program(new RuleDecl("Threshold", List.<RuleClause>of(
                new LetBinding("fee", money("100")),
                new WhenStmt(
                        new BinaryOpExpr(BinaryOp.GT, new NameRef(ref("fee")), money("0")),
                        new PostStmt(
                                Optional.of(new NameRef(ref("fee"))),
                                qrefAccount("ledger", "account", "Performance Fee"),
                                Optional.empty()),
                        Optional.empty()))));

        EvaluationResult r = new Evaluator().evaluate(p, minimalCtx());

        assertThat(r.postings()).hasSize(1);
        assertThat(r.postings().get(0).account()).contains("Performance Fee");
    }

    @Test
    void whenThenElseSkipsThenWhenFalse() {
        Program p = program(new RuleDecl("Threshold", List.<RuleClause>of(
                new LetBinding("fee", money("0")),
                new WhenStmt(
                        new BinaryOpExpr(BinaryOp.GT, new NameRef(ref("fee")), money("0")),
                        new PostStmt(
                                Optional.of(new NameRef(ref("fee"))),
                                qrefAccount("ledger", "account", "Performance Fee"),
                                Optional.empty()),
                        Optional.empty()))));

        EvaluationResult r = new Evaluator().evaluate(p, minimalCtx());

        assertThat(r.postings()).isEmpty();
    }

    @Test
    void mathFunctionRoundOnMoneyLikeNumbers() {
        Program p = program(new RuleDecl("Round", List.<RuleClause>of(
                new LetBinding("rounded", new FunctionCallExpr("round",
                        List.of(num("123.4567"), num("2")))),
                new PublishStmt(new NameRef(ref("rounded")), Optional.empty()))));

        EvaluationResult r = new Evaluator().evaluate(p, minimalCtx());

        NumberVal n = (NumberVal) r.outputs().get("Round:rounded");
        assertThat(n.value()).isEqualByComparingTo("123.46");
    }

    @Test
    void phrasalReferenceResolvesViaDataSource() {
        Program p = program(new RuleDecl("Phrasal", List.<RuleClause>of(
                new LetBinding("x", new NameRef(ref("opening", "nav"))),
                new PublishStmt(new NameRef(ref("x")), Optional.empty()))));

        DataSource ds = MapDataSource.builder()
                .put("opening nav", new MoneyVal(Money.of(new BigDecimal("5000000"), USD)))
                .build();
        EvaluationContext ctx = new EvaluationContext(
                new BusinessDate(LocalDate.of(2026, 3, 31), WeekendOnlyCalendar.DEFAULT),
                ds,
                WeekendOnlyCalendar.DEFAULT,
                AuditSink.discarding());

        EvaluationResult r = new Evaluator().evaluate(p, ctx);

        MoneyVal x = (MoneyVal) r.outputs().get("Phrasal:x");
        assertThat(x.value().amount()).isEqualByComparingTo("5000000.00");
    }
}
