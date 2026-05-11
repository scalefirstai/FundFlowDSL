package ai.getfundflow.dsl.runtime;

import ai.getfundflow.dsl.ast.AccrueStmt;
import ai.getfundflow.dsl.ast.AggregationCall;
import ai.getfundflow.dsl.ast.AllocateStmt;
import ai.getfundflow.dsl.ast.AllocationMethod;
import ai.getfundflow.dsl.ast.AsOfExpr;
import ai.getfundflow.dsl.ast.AtBoundaryExpr;
import ai.getfundflow.dsl.ast.BinaryOpExpr;
import ai.getfundflow.dsl.ast.DateExpr;
import ai.getfundflow.dsl.ast.DayCountExpr;
import ai.getfundflow.dsl.ast.DistributeStmt;
import ai.getfundflow.dsl.ast.Expression;
import ai.getfundflow.dsl.ast.FunctionCallExpr;
import ai.getfundflow.dsl.ast.LetBinding;
import ai.getfundflow.dsl.ast.Literal;
import ai.getfundflow.dsl.ast.NameRef;
import ai.getfundflow.dsl.ast.NotExpr;
import ai.getfundflow.dsl.ast.NounPhrase;
import ai.getfundflow.dsl.ast.NounPhrase.NounAtom;
import ai.getfundflow.dsl.ast.OverExpr;
import ai.getfundflow.dsl.ast.PerAnnumExpr;
import ai.getfundflow.dsl.ast.PeriodExpr;
import ai.getfundflow.dsl.ast.PolicyDecl;
import ai.getfundflow.dsl.ast.PostStmt;
import ai.getfundflow.dsl.ast.Program;
import ai.getfundflow.dsl.ast.PublishStmt;
import ai.getfundflow.dsl.ast.QualifiedRef;
import ai.getfundflow.dsl.ast.RuleClause;
import ai.getfundflow.dsl.ast.RuleDecl;
import ai.getfundflow.dsl.ast.ScheduleDecl;
import ai.getfundflow.dsl.ast.Statement;
import ai.getfundflow.dsl.ast.TopLevelDecl;
import ai.getfundflow.dsl.ast.WaterfallDecl;
import ai.getfundflow.dsl.ast.WhenStmt;
import ai.getfundflow.dsl.core.calendar.BusinessCalendar;
import ai.getfundflow.dsl.core.types.Actual360;
import ai.getfundflow.dsl.core.types.Actual365;
import ai.getfundflow.dsl.core.types.ActualActual;
import ai.getfundflow.dsl.core.types.BusinessDate;
import ai.getfundflow.dsl.core.types.DayCount;
import ai.getfundflow.dsl.core.types.Money;
import ai.getfundflow.dsl.core.types.Percentage;
import ai.getfundflow.dsl.core.types.Thirty360;
import ai.getfundflow.dsl.runtime.RuntimeValue.BoolVal;
import ai.getfundflow.dsl.runtime.RuntimeValue.DateVal;
import ai.getfundflow.dsl.runtime.RuntimeValue.DayCountVal;
import ai.getfundflow.dsl.runtime.RuntimeValue.ListVal;
import ai.getfundflow.dsl.runtime.RuntimeValue.MoneyVal;
import ai.getfundflow.dsl.runtime.RuntimeValue.NullVal;
import ai.getfundflow.dsl.runtime.RuntimeValue.NumberVal;
import ai.getfundflow.dsl.runtime.RuntimeValue.PercentVal;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

public final class Evaluator implements Interpreter {

    static final String ACCRUAL = "__accrual__";
    static final String ALLOCATIONS = "__allocations__";
    static final String DISTRIBUTE_AMOUNT = "__distribute_amount__";

    @Override
    public EvaluationResult evaluate(Program program, EvaluationContext ctx) {
        Run run = new Run(program, ctx);
        run.evaluateProgram();
        return run.result();
    }

    private static final class Run {
        private static final MathContext MC = MathContext.DECIMAL64;

        private final Program program;
        private final Map<String, WaterfallDecl> waterfalls = new HashMap<>();
        private final Map<String, RuntimeValue> outputs = new TreeMap<>();
        private final List<LedgerEntry> postings = new ArrayList<>();
        private final AuditTrail trail = new AuditTrail();
        private final EvaluationContext rootCtx;

        // Mutable per-run state (asOf shifted by AsOfExpr inside expressions).
        private EvaluationContext ctx;

        Run(Program program, EvaluationContext rootCtx) {
            this.program = program;
            this.rootCtx = rootCtx;
            this.ctx = rootCtx;
            for (TopLevelDecl d : program.declarations()) {
                if (d instanceof WaterfallDecl w) waterfalls.put(w.name(), w);
            }
        }

        EvaluationResult result() {
            for (AuditEntry e : trail.entries()) rootCtx.audit().record(e);
            return new EvaluationResult(outputs, postings, trail);
        }

        void evaluateProgram() {
            for (TopLevelDecl decl : program.declarations()) {
                switch (decl) {
                    case RuleDecl r -> evaluateClauses(r.name(), r.clauses());
                    case ScheduleDecl s -> evaluateClauses(s.name(), s.clauses());
                    case PolicyDecl p -> evaluateClauses(p.name(), p.clauses());
                    case WaterfallDecl w -> { /* run only on demand via distribute */ }
                    default -> { /* type extension: no-op */ }
                }
            }
        }

        private void evaluateClauses(String ruleName, List<RuleClause> clauses) {
            Scope scope = new Scope(ruleName);
            for (RuleClause c : clauses) {
                switch (c) {
                    case LetBinding lb -> bindLet(lb, scope);
                    case Statement s -> executeStatement(s, scope);
                    default -> { /* description/applies-to/effective: no-op */ }
                }
            }
        }

        private void bindLet(LetBinding lb, Scope scope) {
            RuntimeValue v = evaluateExpression(lb.value(), scope);
            scope.bindings().put(lb.name(), v);
            trail.record(new AuditEntry(scope.ruleName(), "let " + lb.name(), Map.of(), v));
        }

        // ---- Statements ----------------------------------------------------

        private void executeStatement(Statement s, Scope scope) {
            switch (s) {
                case AccrueStmt a -> executeAccrue(a, scope);
                case AllocateStmt a -> executeAllocate(a, scope);
                case DistributeStmt d -> executeDistribute(d, scope);
                case PostStmt p -> executePost(p, scope);
                case PublishStmt p -> executePublish(p, scope);
                case WhenStmt w -> executeWhen(w, scope);
            }
        }

        private void executeAccrue(AccrueStmt a, Scope scope) {
            RuntimeValue rateV = evaluateExpression(a.rate(), scope);
            RuntimeValue basisV = evaluateExpression(a.basis(), scope);
            DayCount dc = resolveDayCount(a.dayCount(), scope);
            if (rateV instanceof NullVal || basisV instanceof NullVal) {
                scope.bindings().put(ACCRUAL, NullVal.INSTANCE);
                return;
            }
            BigDecimal rate = asRatio(rateV);
            Money basis = asMoney(basisV);
            LocalDate end = ctx.asOf().date();
            LocalDate start = end.minusDays(1);
            BigDecimal yf = dc.yearFraction(start, end);
            BigDecimal accruedAmount = basis.amount().multiply(rate, MC).multiply(yf, MC);
            Money accrued = Money.of(accruedAmount, basis.currency());
            scope.bindings().put(ACCRUAL, new MoneyVal(accrued));
            trail.record(new AuditEntry(
                    scope.ruleName(),
                    "accrue",
                    Map.of("rate", rateV, "basis", basisV, "yearFraction", new NumberVal(yf)),
                    new MoneyVal(accrued)));
        }

        private void executeAllocate(AllocateStmt a, Scope scope) {
            RuntimeValue amountV = evaluateExpression(a.amount(), scope);
            RuntimeValue targetV = evaluateExpression(a.target(), scope);
            if (!(amountV instanceof MoneyVal mv)) {
                scope.bindings().put(ALLOCATIONS, NullVal.INSTANCE);
                return;
            }
            if (!(targetV instanceof ListVal lv)) {
                scope.bindings().put(ALLOCATIONS, NullVal.INSTANCE);
                return;
            }
            List<Money> allocations = (a.method() instanceof AllocationMethod.Equally)
                    ? allocateEqually(mv.value(), lv.values().size())
                    : allocateProRata(mv.value(), toWeights(lv.values()));

            List<RuntimeValue> wrapped = allocations.stream()
                    .map(m -> (RuntimeValue) new MoneyVal(m))
                    .toList();
            ListVal allocList = new ListVal(wrapped);
            scope.bindings().put(ALLOCATIONS, allocList);
            trail.record(new AuditEntry(
                    scope.ruleName(),
                    "allocate (" + (a.method() instanceof AllocationMethod.Equally ? "equally" : "pro-rata") + ")",
                    Map.of("amount", amountV, "n", new NumberVal(BigDecimal.valueOf(allocations.size()))),
                    allocList));
        }

        private List<Money> allocateProRata(Money amount, List<BigDecimal> weights) {
            BigDecimal totalWeight = weights.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            if (totalWeight.signum() == 0) {
                return allocateEqually(amount, weights.size());
            }
            int scale = amount.amount().scale();
            List<Money> out = new ArrayList<>();
            BigDecimal allocatedSoFar = BigDecimal.ZERO;
            for (int i = 0; i < weights.size(); i++) {
                BigDecimal share;
                if (i == weights.size() - 1) {
                    // Last allocation absorbs rounding residual.
                    share = amount.amount().subtract(allocatedSoFar);
                } else {
                    share = amount.amount()
                            .multiply(weights.get(i), MC)
                            .divide(totalWeight, MC)
                            .setScale(scale, RoundingMode.HALF_EVEN);
                    allocatedSoFar = allocatedSoFar.add(share);
                }
                out.add(Money.of(share, amount.currency()));
            }
            return out;
        }

        private List<Money> allocateEqually(Money amount, int n) {
            if (n <= 0) return List.of();
            int scale = amount.amount().scale();
            BigDecimal each = amount.amount()
                    .divide(BigDecimal.valueOf(n), MC)
                    .setScale(scale, RoundingMode.HALF_EVEN);
            List<Money> out = new ArrayList<>();
            BigDecimal allocatedSoFar = BigDecimal.ZERO;
            for (int i = 0; i < n - 1; i++) {
                out.add(Money.of(each, amount.currency()));
                allocatedSoFar = allocatedSoFar.add(each);
            }
            out.add(Money.of(amount.amount().subtract(allocatedSoFar), amount.currency()));
            return out;
        }

        private List<BigDecimal> toWeights(List<RuntimeValue> values) {
            List<BigDecimal> out = new ArrayList<>();
            for (RuntimeValue v : values) {
                out.add(switch (v) {
                    case NumberVal n -> n.value();
                    case MoneyVal m -> m.value().amount();
                    case PercentVal p -> p.value().asRatio();
                    default -> throw new EvaluationException("allocation weight is not numeric: " + v);
                });
            }
            return out;
        }

        private void executeDistribute(DistributeStmt d, Scope scope) {
            RuntimeValue amountV = evaluateExpression(d.amount(), scope);
            WaterfallDecl waterfall = waterfalls.get(d.waterfallName());
            if (waterfall == null) {
                trail.record(new AuditEntry(
                        scope.ruleName(),
                        "distribute (waterfall not found: " + d.waterfallName() + ")",
                        Map.of("amount", amountV),
                        NullVal.INSTANCE));
                return;
            }
            Scope wfScope = new Scope(waterfall.name());
            wfScope.bindings().put(DISTRIBUTE_AMOUNT, amountV);
            for (WaterfallDecl.WaterfallBody b : waterfall.body()) {
                if (b instanceof LetBinding lb) {
                    bindLet(lb, wfScope);
                } else if (b instanceof Statement s) {
                    executeStatement(s, wfScope);
                }
            }
            trail.record(new AuditEntry(
                    scope.ruleName(),
                    "distribute through " + d.waterfallName(),
                    Map.of("amount", amountV),
                    NullVal.INSTANCE));
        }

        private void executePost(PostStmt p, Scope scope) {
            RuntimeValue subject = p.subject()
                    .map(e -> evaluateExpression(e, scope))
                    .orElseGet(() -> scope.bindings().getOrDefault(ACCRUAL, NullVal.INSTANCE));
            String account = canonicalize(p.target());
            Optional<String> narrative = p.narrative();
            if (subject instanceof ListVal lv) {
                for (RuntimeValue v : lv.values()) postOne(v, account, narrative, scope);
            } else {
                postOne(subject, account, narrative, scope);
            }
        }

        private void postOne(RuntimeValue v, String account, Optional<String> narrative, Scope scope) {
            if (!(v instanceof MoneyVal m)) return;
            postings.add(new LedgerEntry(
                    ctx.asOf().date(), account, m.value(), narrative, scope.ruleName()));
            trail.record(new AuditEntry(
                    scope.ruleName(),
                    "post to " + account,
                    Map.of("amount", v),
                    NullVal.INSTANCE));
        }

        private void executePublish(PublishStmt p, Scope scope) {
            RuntimeValue v = evaluateExpression(p.subject(), scope);
            String key = scope.ruleName() + ":" + describe(p.subject());
            outputs.put(key, v);
            trail.record(new AuditEntry(scope.ruleName(), "publish " + describe(p.subject()), Map.of(), v));
        }

        private void executeWhen(WhenStmt w, Scope scope) {
            RuntimeValue cond = evaluateExpression(w.condition(), scope);
            if (cond instanceof BoolVal b) {
                if (b.value()) executeStatement(w.thenBranch(), scope);
                else w.elseBranch().ifPresent(s -> executeStatement(s, scope));
            }
        }

        // ---- Expressions ---------------------------------------------------

        private RuntimeValue evaluateExpression(Expression e, Scope scope) {
            return switch (e) {
                case Literal lit -> evaluateLiteral(lit);
                case NameRef nr -> resolveName(nr.ref(), scope);
                case BinaryOpExpr b -> evaluateBinary(b, scope);
                case NotExpr n -> {
                    RuntimeValue inner = evaluateExpression(n.expression(), scope);
                    if (inner instanceof BoolVal bv) yield new BoolVal(!bv.value());
                    yield NullVal.INSTANCE;
                }
                case AsOfExpr a -> evaluateAsOf(a, scope);
                case AtBoundaryExpr a -> evaluateAtBoundary(a, scope);
                case OverExpr o -> evaluateOver(o, scope);
                case PerAnnumExpr p -> evaluateExpression(p.expression(), scope);
                case FunctionCallExpr fc -> {
                    List<RuntimeValue> args = fc.arguments().stream()
                            .map(arg -> evaluateExpression(arg, scope))
                            .toList();
                    yield FunctionDispatch.invoke(fc.name(), args);
                }
                case AggregationCall ac -> evaluateAggregation(ac, scope);
            };
        }

        private RuntimeValue evaluateAsOf(AsOfExpr a, Scope scope) {
            // Composite phrasal lookup: when inner is a phrasal NameRef and the date is
            // also phrasal (e.g., `nav as of valuation date`), try the full string first.
            if (a.expression() instanceof NameRef nr
                    && a.date() instanceof DateExpr.Phrasal pd) {
                String key = canonicalize(nr.ref()) + " as of " + canonicalize(pd.ref());
                Optional<RuntimeValue> hit = ctx.data().lookupAsOf(key, ctx.asOf().date());
                if (hit.isPresent()) return hit.get();
            }
            LocalDate newAsOf = resolveDate(a.date(), scope).orElse(ctx.asOf().date());
            EvaluationContext saved = ctx;
            BusinessCalendar cal = ctx.defaultCalendar();
            ctx = new EvaluationContext(
                    new BusinessDate(newAsOf, cal), ctx.data(), cal, ctx.audit());
            try {
                return evaluateExpression(a.expression(), scope);
            } finally {
                ctx = saved;
            }
        }

        private RuntimeValue evaluateAtBoundary(AtBoundaryExpr a, Scope scope) {
            // When the inner expression is a phrasal NameRef and the period is also phrasal,
            // synthesize a fully-qualified key like "nav at end of period" and try the data
            // source first. This lets fixtures supply boundary-specific values directly.
            if (a.expression() instanceof NameRef nr
                    && a.period() instanceof PeriodExpr.NamedOrPhrasal pp) {
                String key = canonicalize(nr.ref())
                        + " at " + (a.boundary() == AtBoundaryExpr.Boundary.START ? "start" : "end")
                        + " of " + canonicalize(pp.name());
                Optional<RuntimeValue> hit = ctx.data().lookupAsOf(key, ctx.asOf().date());
                if (hit.isPresent()) return hit.get();
            }
            return evaluateExpression(a.expression(), scope);
        }

        private RuntimeValue evaluateOver(OverExpr o, Scope scope) {
            RuntimeValue inner = evaluateExpression(o.expression(), scope);
            if (o.dayCount().isEmpty() || inner instanceof NullVal) return inner;
            DayCount dc = resolveDayCount(o.dayCount().get(), scope);
            Optional<LocalDate[]> bounds = periodBounds(o.period());
            if (bounds.isEmpty()) return inner;
            BigDecimal yf = dc.yearFraction(bounds.get()[0], bounds.get()[1]);
            return scaleByYearFraction(inner, yf);
        }

        private RuntimeValue scaleByYearFraction(RuntimeValue inner, BigDecimal yf) {
            return switch (inner) {
                case PercentVal p -> new PercentVal(new Percentage(p.value().asRatio().multiply(yf, MC)));
                case MoneyVal m -> new MoneyVal(Money.of(m.value().amount().multiply(yf, MC), m.value().currency()));
                case NumberVal n -> new NumberVal(n.value().multiply(yf, MC));
                default -> inner;
            };
        }

        private Optional<LocalDate[]> periodBounds(PeriodExpr period) {
            if (period instanceof PeriodExpr.ExplicitFromTo ft) {
                Optional<LocalDate> start = literalDate(ft.start());
                Optional<LocalDate> end = ft.end().flatMap(this::literalDate);
                if (start.isPresent() && end.isPresent()) {
                    return Optional.of(new LocalDate[]{start.get(), end.get()});
                }
            }
            return Optional.empty();
        }

        private Optional<LocalDate> literalDate(DateExpr d) {
            if (d instanceof DateExpr.Literal l) return Optional.of(l.value());
            if (d instanceof DateExpr.Phrasal p) {
                RuntimeValue v = ctx.data().lookup(canonicalize(p.ref())).orElse(NullVal.INSTANCE);
                if (v instanceof DateVal dv) return Optional.of(dv.value());
            }
            return Optional.empty();
        }

        private RuntimeValue evaluateBinary(BinaryOpExpr b, Scope scope) {
            RuntimeValue left = evaluateExpression(b.left(), scope);
            RuntimeValue right = evaluateExpression(b.right(), scope);
            if (left instanceof NullVal || right instanceof NullVal) return NullVal.INSTANCE;
            return switch (b.op()) {
                case ADD -> Arithmetic.add(left, right);
                case SUB -> Arithmetic.subtract(left, right);
                case MUL -> Arithmetic.multiply(left, right);
                case DIV -> Arithmetic.divide(left, right);
                case LT -> Arithmetic.compare(left, right, c -> c < 0);
                case LE -> Arithmetic.compare(left, right, c -> c <= 0);
                case GT -> Arithmetic.compare(left, right, c -> c > 0);
                case GE -> Arithmetic.compare(left, right, c -> c >= 0);
                case EQ -> Arithmetic.compare(left, right, c -> c == 0);
                case NEQ -> Arithmetic.compare(left, right, c -> c != 0);
                case AND -> new BoolVal(asBool(left) && asBool(right));
                case OR -> new BoolVal(asBool(left) || asBool(right));
            };
        }

        private RuntimeValue evaluateAggregation(AggregationCall ac, Scope scope) {
            return switch (ac) {
                case AggregationCall.SumOf so -> {
                    RuntimeValue source = evaluateExpression(so.source(), scope);
                    if (source instanceof ListVal lv) yield Arithmetic.sumList(lv.values());
                    yield NullVal.INSTANCE;
                }
                case AggregationCall.WeightedAverage wa -> evaluateWeightedAverage(wa, scope);
            };
        }

        private RuntimeValue evaluateWeightedAverage(
                AggregationCall.WeightedAverage wa, Scope scope) {
            RuntimeValue source = evaluateExpression(wa.source(), scope);
            RuntimeValue weight = evaluateExpression(wa.weight(), scope);
            if (!(source instanceof ListVal sv) || !(weight instanceof ListVal wv)) {
                return NullVal.INSTANCE;
            }
            if (sv.values().size() != wv.values().size() || sv.values().isEmpty()) {
                return NullVal.INSTANCE;
            }
            BigDecimal num = BigDecimal.ZERO;
            BigDecimal den = BigDecimal.ZERO;
            for (int i = 0; i < sv.values().size(); i++) {
                BigDecimal s = asNumberLike(sv.values().get(i));
                BigDecimal w = asNumberLike(wv.values().get(i));
                num = num.add(s.multiply(w, MC));
                den = den.add(w);
            }
            if (den.signum() == 0) return NullVal.INSTANCE;
            return new NumberVal(num.divide(den, MC));
        }

        private BigDecimal asNumberLike(RuntimeValue v) {
            return switch (v) {
                case NumberVal n -> n.value();
                case MoneyVal m -> m.value().amount();
                case PercentVal p -> p.value().asRatio();
                default -> throw new EvaluationException("expected numeric, got " + v);
            };
        }

        // ---- Atoms & lookups ----------------------------------------------

        private RuntimeValue evaluateLiteral(Literal lit) {
            return switch (lit) {
                case Literal.MoneyLit m -> new MoneyVal(m.value());
                case Literal.DateLit d -> new DateVal(d.value());
                case Literal.PercentLit p -> new PercentVal(p.value());
                case Literal.BpsLit b -> new PercentVal(Percentage.ofBps(b.bps()));
                case Literal.DayCountLit dc -> new DayCountVal(dc.value());
                case Literal.NumberLit n -> new NumberVal(n.value());
                case Literal.StringLit s -> new RuntimeValue.StringVal(s.value());
            };
        }

        private RuntimeValue resolveName(QualifiedRef ref, Scope scope) {
            if (ref.phrases().size() == 1
                    && ref.phrases().get(0).atoms().size() == 1
                    && ref.phrases().get(0).atoms().get(0) instanceof NounAtom.Ident id) {
                RuntimeValue bound = scope.bindings().get(id.text());
                if (bound != null) return bound;
            }
            String key = canonicalize(ref);
            // Phrasal magic names — special bindings that survive across statements.
            RuntimeValue accrual = scope.bindings().get(ACCRUAL);
            if (accrual != null && key.equals("accrued amount")) return accrual;
            RuntimeValue allocations = scope.bindings().get(ALLOCATIONS);
            if (allocations != null && key.equals("each allocation")) return allocations;
            RuntimeValue distribute = scope.bindings().get(DISTRIBUTE_AMOUNT);
            if (distribute != null && key.equals("distributable")) return distribute;

            return ctx.data().lookupAsOf(key, ctx.asOf().date()).orElse(NullVal.INSTANCE);
        }

        private Optional<LocalDate> resolveDate(DateExpr d, Scope scope) {
            return switch (d) {
                case DateExpr.Literal l -> Optional.of(l.value());
                case DateExpr.Inception i -> Optional.empty();
                case DateExpr.Phrasal p -> {
                    RuntimeValue v = ctx.data().lookup(canonicalize(p.ref())).orElse(NullVal.INSTANCE);
                    yield (v instanceof DateVal dv) ? Optional.of(dv.value()) : Optional.empty();
                }
            };
        }

        private DayCount resolveDayCount(DayCountExpr dc, Scope scope) {
            return switch (dc) {
                case DayCountExpr.Literal l -> l.value();
                case DayCountExpr.Reference r -> {
                    String key = canonicalize(r.ref());
                    RuntimeValue v = scope.bindings().get(keyBoundLookup(r.ref()));
                    if (v instanceof DayCountVal dv) yield dv.value();
                    RuntimeValue dataV = ctx.data().lookup(key).orElse(NullVal.INSTANCE);
                    if (dataV instanceof DayCountVal dv) yield dv.value();
                    // Fall back: if the key matches a known code text, parse it.
                    yield switch (key) {
                        case "actual/360" -> Actual360.INSTANCE;
                        case "actual/365" -> Actual365.INSTANCE;
                        case "30/360" -> Thirty360.INSTANCE;
                        case "actual/actual" -> ActualActual.INSTANCE;
                        default -> Actual365.INSTANCE;
                    };
                }
            };
        }

        private String keyBoundLookup(QualifiedRef ref) {
            // For single-IDENT references, use the binding key directly.
            if (ref.phrases().size() == 1
                    && ref.phrases().get(0).atoms().size() == 1
                    && ref.phrases().get(0).atoms().get(0) instanceof NounAtom.Ident id) {
                return id.text();
            }
            return canonicalize(ref);
        }

        // ---- Helpers ------------------------------------------------------

        private static BigDecimal asRatio(RuntimeValue v) {
            return switch (v) {
                case PercentVal p -> p.value().asRatio();
                case NumberVal n -> n.value();
                default -> throw new EvaluationException("expected a rate (Percentage/Number), got " + v);
            };
        }

        private static Money asMoney(RuntimeValue v) {
            if (v instanceof MoneyVal m) return m.value();
            throw new EvaluationException("expected Money, got " + v);
        }

        private static boolean asBool(RuntimeValue v) {
            if (v instanceof BoolVal b) return b.value();
            throw new EvaluationException("expected Boolean, got " + v);
        }

        private static String describe(Expression e) {
            return switch (e) {
                case NameRef nr -> canonicalize(nr.ref());
                case AsOfExpr a -> describe(a.expression()) + " as of " + describeDate(a.date());
                case AtBoundaryExpr a -> describe(a.expression())
                        + (a.boundary() == AtBoundaryExpr.Boundary.START ? " at start of " : " at end of ")
                        + describePeriod(a.period());
                default -> "expression";
            };
        }

        private static String describeDate(ai.getfundflow.dsl.ast.DateExpr d) {
            return switch (d) {
                case ai.getfundflow.dsl.ast.DateExpr.Literal l -> l.value().toString();
                case ai.getfundflow.dsl.ast.DateExpr.Inception i -> "inception";
                case ai.getfundflow.dsl.ast.DateExpr.Phrasal p -> canonicalize(p.ref());
            };
        }

        private static String describePeriod(PeriodExpr p) {
            return switch (p) {
                case PeriodExpr.NamedOrPhrasal np -> canonicalize(np.name());
                case PeriodExpr.FromInception fi -> "from inception";
                case PeriodExpr.ExplicitFromTo ft -> "from " + describeDate(ft.start())
                        + ft.end().map(e -> " to " + describeDate(e)).orElse("");
            };
        }
    }

    static String canonicalize(QualifiedRef ref) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ref.phrases().size(); i++) {
            if (i > 0) sb.append(" of ");
            NounPhrase np = ref.phrases().get(i);
            for (int j = 0; j < np.atoms().size(); j++) {
                if (j > 0) sb.append(' ');
                NounAtom a = np.atoms().get(j);
                switch (a) {
                    case NounAtom.Ident id -> sb.append(id.text());
                    case NounAtom.Quoted q -> sb.append('"').append(q.value()).append('"');
                    case NounAtom.Number n -> sb.append(n.text());
                }
            }
        }
        return sb.toString();
    }

    public static final class Scope {
        private final String ruleName;
        private final Map<String, RuntimeValue> bindings = new LinkedHashMap<>();

        public Scope(String ruleName) {
            this.ruleName = ruleName;
        }

        public String ruleName() {
            return ruleName;
        }

        public Map<String, RuntimeValue> bindings() {
            return bindings;
        }
    }
}
