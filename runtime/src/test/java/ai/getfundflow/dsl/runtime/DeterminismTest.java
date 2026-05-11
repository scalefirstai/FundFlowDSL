package ai.getfundflow.dsl.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import ai.getfundflow.dsl.ast.BinaryOpExpr;
import ai.getfundflow.dsl.ast.BinaryOpExpr.BinaryOp;
import ai.getfundflow.dsl.ast.LetBinding;
import ai.getfundflow.dsl.ast.Literal;
import ai.getfundflow.dsl.ast.NameRef;
import ai.getfundflow.dsl.ast.NounPhrase;
import ai.getfundflow.dsl.ast.NounPhrase.NounAtom;
import ai.getfundflow.dsl.ast.Program;
import ai.getfundflow.dsl.ast.PublishStmt;
import ai.getfundflow.dsl.ast.QualifiedRef;
import ai.getfundflow.dsl.ast.RuleClause;
import ai.getfundflow.dsl.ast.RuleDecl;
import ai.getfundflow.dsl.ast.TopLevelDecl;
import ai.getfundflow.dsl.core.calendar.WeekendOnlyCalendar;
import ai.getfundflow.dsl.core.types.BusinessDate;
import ai.getfundflow.dsl.core.types.Money;
import ai.getfundflow.dsl.core.types.Percentage;
import ai.getfundflow.dsl.runtime.RuntimeValue.MoneyVal;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DeterminismTest {

    private static final Currency USD = Currency.getInstance("USD");

    private static QualifiedRef ref(String atom) {
        return new QualifiedRef(List.of(new NounPhrase(List.of(new NounAtom.Ident(atom)))));
    }

    private static QualifiedRef phrasal(String first, String second) {
        return new QualifiedRef(List.of(new NounPhrase(List.of(
                new NounAtom.Ident(first), new NounAtom.Ident(second)))));
    }

    private Program syntheticProgram() {
        RuleDecl r = new RuleDecl("Mgmt Fee", List.<RuleClause>of(
                new LetBinding("nav", new NameRef(phrasal("opening", "nav"))),
                new LetBinding("rate",
                        new Literal.PercentLit(Percentage.ofPercent("1.5"))),
                new LetBinding("fee", new BinaryOpExpr(BinaryOp.MUL,
                        new NameRef(ref("nav")), new NameRef(ref("rate")))),
                new PublishStmt(new NameRef(ref("fee")), Optional.empty())));
        return new Program(Optional.empty(), List.of(), List.<TopLevelDecl>of(r));
    }

    private EvaluationContext contextWithInputs(Map<String, RuntimeValue> inputs) {
        DataSource ds = MapDataSource.of(inputs);
        return new EvaluationContext(
                new BusinessDate(LocalDate.of(2026, 3, 31), WeekendOnlyCalendar.DEFAULT),
                ds,
                WeekendOnlyCalendar.DEFAULT,
                AuditSink.discarding());
    }

    @Test
    void rerunningTheSameProgram1000xYieldsIdenticalAuditHash() {
        Program p = syntheticProgram();
        Map<String, RuntimeValue> inputs = Map.of(
                "opening nav", new MoneyVal(Money.of(new BigDecimal("10000000"), USD)));

        Set<String> hashes = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            EvaluationResult r = new Evaluator().evaluate(p, contextWithInputs(inputs));
            hashes.add(r.trail().contentHash());
        }
        assertThat(hashes).hasSize(1);
    }

    @Test
    void shufflingInputMapOrderDoesNotChangeOutputs() {
        Program p = syntheticProgram();
        // Two equivalent maps, populated in different insertion orders.
        Map<String, RuntimeValue> a = new LinkedHashMap<>();
        a.put("opening nav", new MoneyVal(Money.of(new BigDecimal("10000000"), USD)));
        a.put("noise.alpha", new MoneyVal(Money.of(new BigDecimal("1"), USD)));
        a.put("noise.beta", new MoneyVal(Money.of(new BigDecimal("2"), USD)));

        Map<String, RuntimeValue> b = new LinkedHashMap<>();
        b.put("noise.beta", new MoneyVal(Money.of(new BigDecimal("2"), USD)));
        b.put("noise.alpha", new MoneyVal(Money.of(new BigDecimal("1"), USD)));
        b.put("opening nav", new MoneyVal(Money.of(new BigDecimal("10000000"), USD)));

        EvaluationResult ra = new Evaluator().evaluate(p, contextWithInputs(a));
        EvaluationResult rb = new Evaluator().evaluate(p, contextWithInputs(b));

        assertThat(rb.outputs()).isEqualTo(ra.outputs());
        assertThat(rb.trail().contentHash()).isEqualTo(ra.trail().contentHash());
    }
}
