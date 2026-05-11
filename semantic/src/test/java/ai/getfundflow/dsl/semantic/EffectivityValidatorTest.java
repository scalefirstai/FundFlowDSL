package ai.getfundflow.dsl.semantic;

import static org.assertj.core.api.Assertions.assertThat;

import ai.getfundflow.dsl.ast.AppliesToClause;
import ai.getfundflow.dsl.ast.DateExpr;
import ai.getfundflow.dsl.ast.EffectiveClause;
import ai.getfundflow.dsl.ast.NounPhrase;
import ai.getfundflow.dsl.ast.NounPhrase.NounAtom;
import ai.getfundflow.dsl.ast.PeriodExpr;
import ai.getfundflow.dsl.ast.Program;
import ai.getfundflow.dsl.ast.QualifiedRef;
import ai.getfundflow.dsl.ast.RuleClause;
import ai.getfundflow.dsl.ast.RuleDecl;
import ai.getfundflow.dsl.ast.TopLevelDecl;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class EffectivityValidatorTest {

    private static QualifiedRef sel(String... words) {
        List<NounAtom> atoms = java.util.Arrays.stream(words)
                .map(w -> (NounAtom) new NounAtom.Ident(w))
                .toList();
        return new QualifiedRef(List.of(new NounPhrase(atoms)));
    }

    private static RuleDecl rule(String name, QualifiedRef selector, LocalDate start, LocalDate end) {
        DateExpr s = new DateExpr.Literal(start);
        Optional<DateExpr> e = end == null
                ? Optional.empty()
                : Optional.of(new DateExpr.Literal(end));
        List<RuleClause> clauses = List.of(
                new AppliesToClause(selector),
                new EffectiveClause(new PeriodExpr.ExplicitFromTo(s, e)));
        return new RuleDecl(name, clauses);
    }

    private static Program prog(RuleDecl... rules) {
        return new Program(
                Optional.empty(),
                List.of(),
                java.util.Arrays.stream(rules).map(r -> (TopLevelDecl) r).toList());
    }

    @Test
    void overlappingPeriodsSameSelectorEmitDiagnostic() {
        Program p = prog(
                rule("A", sel("fund", "X"), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30)),
                rule("B", sel("fund", "X"), LocalDate.of(2026, 4, 1), LocalDate.of(2026, 12, 31)));
        Diagnostics d = new Diagnostics();
        new EffectivityValidator(d).validate(p);
        assertThat(d.errors())
                .extracting(diag -> diag.code())
                .contains(DiagnosticCode.EFFECTIVE_OVERLAP);
    }

    @Test
    void disjointPeriodsAreOk() {
        Program p = prog(
                rule("A", sel("fund", "X"), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30)),
                rule("B", sel("fund", "X"), LocalDate.of(2026, 7, 1), LocalDate.of(2026, 12, 31)));
        Diagnostics d = new Diagnostics();
        new EffectivityValidator(d).validate(p);
        assertThat(d.errors()).isEmpty();
    }

    @Test
    void differentSelectorsDoNotConflict() {
        Program p = prog(
                rule("A", sel("fund", "X"), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)),
                rule("B", sel("fund", "Y"), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)));
        Diagnostics d = new Diagnostics();
        new EffectivityValidator(d).validate(p);
        assertThat(d.errors()).isEmpty();
    }

    @Test
    void invertedPeriodEmitsDiagnostic() {
        Program p = prog(
                rule("Bad", sel("fund", "X"),
                        LocalDate.of(2026, 12, 31), LocalDate.of(2026, 1, 1)));
        Diagnostics d = new Diagnostics();
        new EffectivityValidator(d).validate(p);
        assertThat(d.errors())
                .extracting(diag -> diag.code())
                .contains(DiagnosticCode.INVERTED_PERIOD);
    }

    @Test
    void openEndedPeriodTreatedAsFarFuture() {
        Program p = prog(
                rule("A", sel("fund", "X"), LocalDate.of(2026, 1, 1), null),
                rule("B", sel("fund", "X"), LocalDate.of(2027, 1, 1), null));
        Diagnostics d = new Diagnostics();
        new EffectivityValidator(d).validate(p);
        assertThat(d.errors())
                .extracting(diag -> diag.code())
                .contains(DiagnosticCode.EFFECTIVE_OVERLAP);
    }
}
