package ai.getfundflow.dsl.semantic;

import ai.getfundflow.dsl.ast.AccrueStmt;
import ai.getfundflow.dsl.ast.AggregationCall;
import ai.getfundflow.dsl.ast.AllocateStmt;
import ai.getfundflow.dsl.ast.AllocationMethod;
import ai.getfundflow.dsl.ast.AsOfExpr;
import ai.getfundflow.dsl.ast.AtBoundaryExpr;
import ai.getfundflow.dsl.ast.BinaryOpExpr;
import ai.getfundflow.dsl.ast.BinaryOpExpr.BinaryOp;
import ai.getfundflow.dsl.ast.DateExpr;
import ai.getfundflow.dsl.ast.DayCountExpr;
import ai.getfundflow.dsl.ast.DistributeStmt;
import ai.getfundflow.dsl.ast.Expression;
import ai.getfundflow.dsl.ast.FunctionCallExpr;
import ai.getfundflow.dsl.ast.LetBinding;
import ai.getfundflow.dsl.ast.Literal;
import ai.getfundflow.dsl.ast.NameRef;
import ai.getfundflow.dsl.ast.NotExpr;
import ai.getfundflow.dsl.ast.OverExpr;
import ai.getfundflow.dsl.ast.PerAnnumExpr;
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
import ai.getfundflow.dsl.ast.TypeExtensionDecl;
import ai.getfundflow.dsl.ast.WaterfallDecl;
import ai.getfundflow.dsl.ast.WhenStmt;
import ai.getfundflow.dsl.semantic.DslType.BigDecimalType;
import ai.getfundflow.dsl.semantic.DslType.BooleanType;
import ai.getfundflow.dsl.semantic.DslType.BusinessDateType;
import ai.getfundflow.dsl.semantic.DslType.DayCountType;
import ai.getfundflow.dsl.semantic.DslType.MoneyType;
import ai.getfundflow.dsl.semantic.DslType.PercentageType;
import ai.getfundflow.dsl.semantic.DslType.PeriodType;
import ai.getfundflow.dsl.semantic.DslType.StringType;
import ai.getfundflow.dsl.semantic.DslType.UnknownType;
import ai.getfundflow.dsl.semantic.Symbol.BindingSymbol;
import ai.getfundflow.dsl.stdlib.FunctionRegistry;
import java.math.BigDecimal;
import java.util.Optional;

public final class TypeChecker {

    private static final BigDecimal LARGE_SCALAR_THRESHOLD = new BigDecimal("1000");

    private final SymbolTable symbols;
    private final Diagnostics diagnostics;
    private final SourceMap sourceMap;
    private final TypeRegistry types;
    private String currentOwner;
    private Object currentNode;

    public TypeChecker(SymbolTable symbols, Diagnostics diagnostics) {
        this(symbols, diagnostics, SourceMap.EMPTY, new TypeRegistry());
    }

    public TypeChecker(SymbolTable symbols, Diagnostics diagnostics, SourceMap sourceMap) {
        this(symbols, diagnostics, sourceMap, new TypeRegistry());
    }

    public TypeChecker(SymbolTable symbols, Diagnostics diagnostics, SourceMap sourceMap, TypeRegistry types) {
        this.symbols = symbols;
        this.diagnostics = diagnostics;
        this.sourceMap = sourceMap;
        this.types = types;
    }

    public void check(Program program) {
        for (TopLevelDecl decl : program.declarations()) {
            checkDecl(decl);
        }
    }

    private void checkDecl(TopLevelDecl decl) {
        switch (decl) {
            case RuleDecl r -> {
                currentOwner = r.name();
                for (RuleClause c : r.clauses()) {
                    checkClause(c);
                }
            }
            case ScheduleDecl s -> {
                currentOwner = s.name();
                for (RuleClause c : s.clauses()) {
                    checkClause(c);
                }
            }
            case PolicyDecl p -> {
                currentOwner = p.name();
                for (RuleClause c : p.clauses()) {
                    checkClause(c);
                }
            }
            case WaterfallDecl w -> {
                currentOwner = w.name();
                for (WaterfallDecl.WaterfallBody b : w.body()) {
                    if (b instanceof LetBinding lb) {
                        checkLet(lb);
                    } else if (b instanceof Statement s) {
                        checkStatement(s);
                    }
                }
            }
            case TypeExtensionDecl t -> { /* no-op for now; WP-11 */ }
        }
    }

    private void checkClause(RuleClause clause) {
        switch (clause) {
            case LetBinding lb -> checkLet(lb);
            case Statement s -> checkStatement(s);
            default -> { /* description / applies-to / effective don't introduce types */ }
        }
    }

    private void checkLet(LetBinding lb) {
        currentNode = lb;
        DslType type = inferExpression(lb.value());
        BindingSymbol binding = new BindingSymbol(lb.name(), type);
        if (!symbols.registerBinding(currentOwner, binding)) {
            diagnostics.add(Diagnostic.of(
                    DiagnosticCode.DUPLICATE_DECLARATION,
                    sourceMap.locationOf(lb),
                    "duplicate let binding '" + lb.name() + "' in '" + currentOwner + "'"));
        }
    }

    private void checkStatement(Statement s) {
        currentNode = s;
        switch (s) {
            case AccrueStmt a -> {
                DslType rateType = inferExpression(a.rate());
                DslType basisType = inferExpression(a.basis());
                if (!(rateType instanceof PercentageType
                        || rateType instanceof BigDecimalType
                        || rateType instanceof UnknownType)) {
                    error(DiagnosticCode.TYPE_MISMATCH,
                            "accrue rate must be a Percentage, got " + rateType.describe());
                }
                if (!(basisType instanceof MoneyType || basisType instanceof UnknownType)) {
                    error(DiagnosticCode.TYPE_MISMATCH,
                            "accrue basis must be Money, got " + basisType.describe());
                }
            }
            case AllocateStmt a -> {
                DslType amount = inferExpression(a.amount());
                if (!(amount instanceof MoneyType || amount instanceof UnknownType)) {
                    error(DiagnosticCode.TYPE_MISMATCH,
                            "allocate amount must be Money, got " + amount.describe());
                }
                inferExpression(a.target());
                if (a.method() instanceof AllocationMethod.ProRata pr) {
                    inferExpression(pr.weight());
                }
            }
            case DistributeStmt d -> {
                DslType amount = inferExpression(d.amount());
                if (!(amount instanceof MoneyType || amount instanceof UnknownType)) {
                    error(DiagnosticCode.TYPE_MISMATCH,
                            "distribute amount must be Money, got " + amount.describe());
                }
            }
            case PostStmt p -> p.subject().ifPresent(this::inferExpression);
            case PublishStmt p -> inferExpression(p.subject());
            case WhenStmt w -> {
                DslType cond = inferExpression(w.condition());
                if (!(cond instanceof BooleanType || cond instanceof UnknownType)) {
                    error(DiagnosticCode.TYPE_MISMATCH,
                            "when condition must be Boolean, got " + cond.describe());
                }
                checkStatement(w.thenBranch());
                w.elseBranch().ifPresent(this::checkStatement);
            }
        }
    }

    // --- Expression type inference -----------------------------------------

    public DslType inferExpression(Expression e) {
        currentNode = e;
        return switch (e) {
            case Literal lit -> inferLiteral(lit);
            case NameRef nr -> resolveNameRef(nr);
            case BinaryOpExpr b -> inferBinaryOp(b);
            case NotExpr n -> {
                DslType inner = inferExpression(n.expression());
                if (!(inner instanceof BooleanType || inner instanceof UnknownType)) {
                    error(DiagnosticCode.INVALID_OPERAND,
                            "'not' requires Boolean, got " + inner.describe());
                }
                yield BooleanType.INSTANCE;
            }
            case AsOfExpr a -> inferExpression(a.expression());
            case AtBoundaryExpr a -> inferExpression(a.expression());
            case OverExpr o -> inferExpression(o.expression());
            case PerAnnumExpr p -> inferExpression(p.expression());
            case FunctionCallExpr fc -> inferFunctionCall(fc);
            case AggregationCall ac -> inferAggregation(ac);
        };
    }

    private DslType inferLiteral(Literal lit) {
        return switch (lit) {
            case Literal.MoneyLit m -> new MoneyType(m.value().currency());
            case Literal.DateLit d -> BusinessDateType.INSTANCE;
            case Literal.PercentLit p -> PercentageType.INSTANCE;
            case Literal.BpsLit b -> PercentageType.INSTANCE;
            case Literal.DayCountLit dc -> DayCountType.INSTANCE;
            case Literal.NumberLit n -> BigDecimalType.INSTANCE;
            case Literal.StringLit s -> StringType.INSTANCE;
        };
    }

    private DslType resolveNameRef(NameRef nr) {
        QualifiedRef ref = nr.ref();
        // Single-IDENT, single-phrase, no 'of': this is a let-binding lookup
        if (ref.phrases().size() == 1
                && ref.phrases().get(0).atoms().size() == 1
                && ref.phrases().get(0).atoms().get(0)
                        instanceof ai.getfundflow.dsl.ast.NounPhrase.NounAtom.Ident id) {
            return symbols.lookupBinding(currentOwner, id.text())
                    .map(BindingSymbol::type)
                    .orElseGet(() -> {
                        Diagnostic d = Diagnostic.of(
                                DiagnosticCode.UNRESOLVED_BINDING,
                                sourceMap.locationOf(nr),
                                "unresolved binding '" + id.text() + "' in '" + currentOwner + "'");
                        Optional<String> suggestion = closestBinding(id.text());
                        if (suggestion.isPresent()) {
                            d = d.withHint("did you mean '" + suggestion.get() + "'?");
                        }
                        diagnostics.add(d);
                        return UnknownType.INSTANCE;
                    });
        }
        // Field-on-entity phrasal: `<field_ident> of <base_type_ident> ["name"]`
        DslType extensionField = tryResolveExtensionField(nr, ref);
        if (extensionField != null) return extensionField;

        // Multi-word or qualified phrasal — defer to per-fund domain catalog.
        diagnostics.add(Diagnostic.of(
                DiagnosticCode.DEFERRED_REFERENCE,
                sourceMap.locationOf(nr),
                "deferred phrasal reference"));
        return UnknownType.INSTANCE;
    }

    /**
     * Pattern: {@code commitment_period of fund "Acme PE"}.
     *
     * <p>The first phrase is a single IDENT (the field name). The second phrase starts
     * with a known base-type IDENT (Fund / fund / Investor / investor / etc.) and may
     * be followed by a quoted entity name. Looks up the field on any registered
     * extension of that base type.
     */
    private DslType tryResolveExtensionField(NameRef nr, QualifiedRef ref) {
        if (ref.phrases().size() != 2) return null;
        var fieldPhrase = ref.phrases().get(0);
        var entityPhrase = ref.phrases().get(1);
        if (fieldPhrase.atoms().size() != 1
                || !(fieldPhrase.atoms().get(0)
                        instanceof ai.getfundflow.dsl.ast.NounPhrase.NounAtom.Ident fieldIdent)) {
            return null;
        }
        if (entityPhrase.atoms().isEmpty()
                || !(entityPhrase.atoms().get(0)
                        instanceof ai.getfundflow.dsl.ast.NounPhrase.NounAtom.Ident entityIdent)) {
            return null;
        }
        if (!types.isBaseType(entityIdent.text())) return null;

        Optional<DslType> fieldType = types.resolveExtensionField(entityIdent.text(), fieldIdent.text());
        if (fieldType.isPresent()) return fieldType.get();

        if (!types.extensions().isEmpty()) {
            diagnostics.add(Diagnostic.of(
                    DiagnosticCode.UNKNOWN_EXTENSION_FIELD,
                    sourceMap.locationOf(nr),
                    "no extension of " + types.canonicalBase(entityIdent.text()).orElse(entityIdent.text())
                            + " declares a field named '" + fieldIdent.text() + "'"));
            return UnknownType.INSTANCE;
        }
        // No extensions declared at all — fall through to deferred-reference behaviour.
        return null;
    }

    private Optional<String> closestBinding(String unknown) {
        String best = null;
        int bestDist = Integer.MAX_VALUE;
        for (String name : symbols.bindingsFor(currentOwner).keySet()) {
            int d = Levenshtein.distance(unknown, name);
            if (d <= 2 && d < bestDist) {
                best = name;
                bestDist = d;
            }
        }
        return Optional.ofNullable(best);
    }

    private DslType inferBinaryOp(BinaryOpExpr expr) {
        DslType left = inferExpression(expr.left());
        DslType right = inferExpression(expr.right());

        return switch (expr.op()) {
            case ADD, SUB -> checkAddSub(left, right);
            case MUL -> checkMul(left, right, expr);
            case DIV -> checkDiv(left, right);
            case LT, LE, GT, GE -> checkOrder(left, right);
            case EQ, NEQ -> checkEquality(left, right);
            case AND, OR -> checkLogical(left, right);
        };
    }

    private DslType checkAddSub(DslType left, DslType right) {
        if (left instanceof UnknownType || right instanceof UnknownType) {
            return UnknownType.INSTANCE;
        }
        if (left instanceof MoneyType lm && right instanceof MoneyType rm) {
            if (!compatibleCurrency(lm, rm)) {
                error(DiagnosticCode.CURRENCY_MISMATCH,
                        "currency mismatch in addition: "
                                + lm.describe() + " vs " + rm.describe());
                return UnknownType.INSTANCE;
            }
            return lm.isAnyCurrency() ? rm : lm;
        }
        if (left instanceof PercentageType && right instanceof PercentageType) {
            return PercentageType.INSTANCE;
        }
        if (left instanceof BigDecimalType && right instanceof BigDecimalType) {
            return BigDecimalType.INSTANCE;
        }
        error(DiagnosticCode.INVALID_OPERAND,
                "cannot add/subtract " + left.describe() + " and " + right.describe());
        return UnknownType.INSTANCE;
    }

    private DslType checkMul(DslType left, DslType right, BinaryOpExpr ctx) {
        if (left instanceof UnknownType || right instanceof UnknownType) {
            return UnknownType.INSTANCE;
        }
        if (left instanceof MoneyType m && right instanceof PercentageType) return m;
        if (left instanceof PercentageType && right instanceof MoneyType m) return m;
        if (left instanceof MoneyType && right instanceof MoneyType) {
            error(DiagnosticCode.MONEY_MULTIPLY_MONEY,
                    "Money * Money is not allowed");
            return UnknownType.INSTANCE;
        }
        if (left instanceof MoneyType m && right instanceof BigDecimalType) {
            warnIfLargeScalar(ctx.right());
            return m;
        }
        if (left instanceof BigDecimalType && right instanceof MoneyType m) {
            warnIfLargeScalar(ctx.left());
            return m;
        }
        if (left instanceof PercentageType && right instanceof PercentageType) {
            return PercentageType.INSTANCE;
        }
        if (left instanceof BigDecimalType && right instanceof BigDecimalType) {
            return BigDecimalType.INSTANCE;
        }
        if (left instanceof BigDecimalType && right instanceof PercentageType) return PercentageType.INSTANCE;
        if (left instanceof PercentageType && right instanceof BigDecimalType) return PercentageType.INSTANCE;
        error(DiagnosticCode.INVALID_OPERAND,
                "cannot multiply " + left.describe() + " and " + right.describe());
        return UnknownType.INSTANCE;
    }

    private DslType checkDiv(DslType left, DslType right) {
        if (left instanceof UnknownType || right instanceof UnknownType) {
            return UnknownType.INSTANCE;
        }
        if (left instanceof MoneyType && right instanceof MoneyType) return BigDecimalType.INSTANCE;
        if (left instanceof MoneyType m && right instanceof BigDecimalType) return m;
        if (left instanceof MoneyType m && right instanceof PercentageType) return m;
        if (left instanceof BigDecimalType && right instanceof BigDecimalType) return BigDecimalType.INSTANCE;
        if (left instanceof PercentageType && right instanceof BigDecimalType) return PercentageType.INSTANCE;
        error(DiagnosticCode.INVALID_OPERAND,
                "cannot divide " + left.describe() + " by " + right.describe());
        return UnknownType.INSTANCE;
    }

    private DslType checkOrder(DslType left, DslType right) {
        if (left instanceof UnknownType || right instanceof UnknownType) {
            return BooleanType.INSTANCE;
        }
        if (left instanceof MoneyType lm && right instanceof MoneyType rm) {
            if (!compatibleCurrency(lm, rm)) {
                error(DiagnosticCode.CURRENCY_MISMATCH,
                        "currency mismatch in comparison: "
                                + lm.describe() + " vs " + rm.describe());
            }
            return BooleanType.INSTANCE;
        }
        if (sameKind(left, right)) {
            return BooleanType.INSTANCE;
        }
        error(DiagnosticCode.INVALID_OPERAND,
                "cannot compare " + left.describe() + " with " + right.describe());
        return BooleanType.INSTANCE;
    }

    private DslType checkEquality(DslType left, DslType right) {
        if (left instanceof UnknownType || right instanceof UnknownType) {
            return BooleanType.INSTANCE;
        }
        if (left instanceof MoneyType lm && right instanceof MoneyType rm) {
            if (!compatibleCurrency(lm, rm)) {
                error(DiagnosticCode.CURRENCY_MISMATCH,
                        "currency mismatch in equality: "
                                + lm.describe() + " vs " + rm.describe());
            }
            return BooleanType.INSTANCE;
        }
        if (sameKind(left, right)) {
            return BooleanType.INSTANCE;
        }
        error(DiagnosticCode.INVALID_OPERAND,
                "cannot test equality of " + left.describe() + " and " + right.describe());
        return BooleanType.INSTANCE;
    }

    private DslType checkLogical(DslType left, DslType right) {
        if (left instanceof UnknownType || right instanceof UnknownType) {
            return BooleanType.INSTANCE;
        }
        if (!(left instanceof BooleanType)) {
            error(DiagnosticCode.INVALID_OPERAND,
                    "logical operator requires Boolean, got " + left.describe());
        }
        if (!(right instanceof BooleanType)) {
            error(DiagnosticCode.INVALID_OPERAND,
                    "logical operator requires Boolean, got " + right.describe());
        }
        return BooleanType.INSTANCE;
    }

    private DslType inferFunctionCall(FunctionCallExpr fc) {
        java.util.List<DslType> argTypes = new java.util.ArrayList<>();
        boolean anyUnknown = false;
        for (Expression arg : fc.arguments()) {
            DslType t = inferExpression(arg);
            argTypes.add(t);
            if (t instanceof UnknownType) anyUnknown = true;
        }
        FunctionRegistry.Signature sig = FunctionRegistry.lookup(fc.name()).orElse(null);
        if (sig == null) {
            error(DiagnosticCode.UNKNOWN_FUNCTION, "unknown function: '" + fc.name() + "'");
            return UnknownType.INSTANCE;
        }
        int arity = fc.arguments().size();
        if (arity < sig.minArity() || arity > sig.maxArity()) {
            error(DiagnosticCode.FUNCTION_ARITY_MISMATCH,
                    "function '" + fc.name() + "' takes "
                            + arityRange(sig) + " arguments, got " + arity);
        }
        if (anyUnknown) return UnknownType.INSTANCE;
        return functionReturnType(fc, sig, argTypes);
    }

    private DslType functionReturnType(
            FunctionCallExpr fc,
            FunctionRegistry.Signature sig,
            java.util.List<DslType> argTypes) {
        return switch (sig.category()) {
            case MATH -> {
                if (fc.name().equals("max") || fc.name().equals("min")) {
                    yield mostSpecific(argTypes);
                }
                yield BigDecimalType.INSTANCE;
            }
            case STATS -> BigDecimalType.INSTANCE;
            case FINANCIAL -> BigDecimalType.INSTANCE;
            case DATE -> switch (fc.name()) {
                case "edate", "eomonth" -> BusinessDateType.INSTANCE;
                default -> BigDecimalType.INSTANCE;
            };
        };
    }

    /** Pick the non-Number type if any arg has one (so max(0, money) → money). */
    private DslType mostSpecific(java.util.List<DslType> types) {
        if (types.isEmpty()) return UnknownType.INSTANCE;
        for (DslType t : types) {
            if (!(t instanceof BigDecimalType)) {
                return t;
            }
        }
        return types.get(0);
    }

    private DslType inferAggregation(AggregationCall ac) {
        return switch (ac) {
            case AggregationCall.SumOf so -> {
                DslType source = inferExpression(so.source());
                so.by().ifPresent(this::inferExpression);
                yield source;
            }
            case AggregationCall.WeightedAverage wa -> {
                inferExpression(wa.source());
                inferExpression(wa.weight());
                yield BigDecimalType.INSTANCE;
            }
        };
    }

    // --- Helpers -------------------------------------------------------------

    private boolean compatibleCurrency(MoneyType a, MoneyType b) {
        if (a.isAnyCurrency() || b.isAnyCurrency()) return true;
        return a.currency().equals(b.currency());
    }

    private boolean sameKind(DslType a, DslType b) {
        return a.getClass() == b.getClass();
    }

    private void warnIfLargeScalar(Expression e) {
        if (e instanceof Literal.NumberLit n
                && n.value().abs().compareTo(LARGE_SCALAR_THRESHOLD) > 0) {
            diagnostics.add(Diagnostic.of(
                    DiagnosticCode.LARGE_SCALAR_ON_MONEY,
                    sourceMap.locationOf(e),
                    "scalar " + n.value().toPlainString() + " > 1000 multiplied by Money"));
        }
    }

    private String arityRange(FunctionRegistry.Signature sig) {
        if (sig.minArity() == sig.maxArity()) {
            return String.valueOf(sig.minArity());
        }
        if (sig.maxArity() == Integer.MAX_VALUE) {
            return sig.minArity() + "+";
        }
        return sig.minArity() + ".." + sig.maxArity();
    }

    private void error(DiagnosticCode code, String message) {
        diagnostics.add(Diagnostic.error(code, sourceMap.locationOf(currentNode), message));
    }
}
