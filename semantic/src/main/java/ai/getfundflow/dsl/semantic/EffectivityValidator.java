package ai.getfundflow.dsl.semantic;

import ai.getfundflow.dsl.ast.AppliesToClause;
import ai.getfundflow.dsl.ast.DateExpr;
import ai.getfundflow.dsl.ast.EffectiveClause;
import ai.getfundflow.dsl.ast.PeriodExpr;
import ai.getfundflow.dsl.ast.Program;
import ai.getfundflow.dsl.ast.QualifiedRef;
import ai.getfundflow.dsl.ast.RuleClause;
import ai.getfundflow.dsl.ast.RuleDecl;
import ai.getfundflow.dsl.ast.TopLevelDecl;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class EffectivityValidator {

    private static final LocalDate FAR_FUTURE = LocalDate.of(9999, 12, 31);

    private final Diagnostics diagnostics;
    private final SourceMap sourceMap;

    public EffectivityValidator(Diagnostics diagnostics) {
        this(diagnostics, SourceMap.EMPTY);
    }

    public EffectivityValidator(Diagnostics diagnostics, SourceMap sourceMap) {
        this.diagnostics = diagnostics;
        this.sourceMap = sourceMap;
    }

    public void validate(Program program) {
        Map<QualifiedRef, List<RuleEffectivity>> grouped = new HashMap<>();
        for (TopLevelDecl decl : program.declarations()) {
            if (!(decl instanceof RuleDecl r)) continue;
            Optional<QualifiedRef> selector = appliesTo(r);
            Optional<DateRange> range = effectiveRange(r);
            if (range.isEmpty()) continue;
            range.get().validateOrdering(diagnostics, sourceMap, r);
            selector.ifPresent(s ->
                    grouped.computeIfAbsent(s, k -> new ArrayList<>())
                            .add(new RuleEffectivity(r, range.get())));
        }
        for (Map.Entry<QualifiedRef, List<RuleEffectivity>> entry : grouped.entrySet()) {
            checkPairs(entry.getValue());
        }
    }

    private void checkPairs(List<RuleEffectivity> rules) {
        for (int i = 0; i < rules.size(); i++) {
            for (int j = i + 1; j < rules.size(); j++) {
                if (rules.get(i).range.overlaps(rules.get(j).range)) {
                    diagnostics.add(Diagnostic.of(
                            DiagnosticCode.EFFECTIVE_OVERLAP,
                            sourceMap.locationOf(rules.get(j).rule),
                            "rules '" + rules.get(i).rule.name() + "' and '"
                                    + rules.get(j).rule.name() + "' have overlapping effective periods"));
                }
            }
        }
    }

    private Optional<QualifiedRef> appliesTo(RuleDecl r) {
        for (RuleClause c : r.clauses()) {
            if (c instanceof AppliesToClause a) return Optional.of(a.selector());
        }
        return Optional.empty();
    }

    private Optional<DateRange> effectiveRange(RuleDecl r) {
        for (RuleClause c : r.clauses()) {
            if (c instanceof EffectiveClause e) return resolveRange(e.period());
        }
        return Optional.empty();
    }

    private Optional<DateRange> resolveRange(PeriodExpr period) {
        if (!(period instanceof PeriodExpr.ExplicitFromTo ft)) return Optional.empty();
        Optional<LocalDate> start = literalDate(ft.start());
        if (start.isEmpty()) return Optional.empty();
        LocalDate end = ft.end().flatMap(this::literalDate).orElse(FAR_FUTURE);
        return Optional.of(new DateRange(start.get(), end));
    }

    private Optional<LocalDate> literalDate(DateExpr d) {
        if (d instanceof DateExpr.Literal l) return Optional.of(l.value());
        return Optional.empty();
    }

    private record RuleEffectivity(RuleDecl rule, DateRange range) {}

    private record DateRange(LocalDate start, LocalDate end) {

        boolean overlaps(DateRange other) {
            return !start.isAfter(other.end) && !other.start.isAfter(end);
        }

        void validateOrdering(Diagnostics diagnostics, SourceMap sourceMap, RuleDecl rule) {
            if (end.isBefore(start)) {
                diagnostics.add(Diagnostic.of(
                        DiagnosticCode.INVERTED_PERIOD,
                        sourceMap.locationOf(rule),
                        "rule '" + rule.name() + "' has period end (" + end
                                + ") before start (" + start + ")"));
            }
        }
    }
}
