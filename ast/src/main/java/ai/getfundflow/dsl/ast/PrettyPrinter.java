package ai.getfundflow.dsl.ast;

import ai.getfundflow.dsl.ast.NounPhrase.NounAtom;
import ai.getfundflow.dsl.core.types.Money;
import java.util.List;

public final class PrettyPrinter {

    private static final String INDENT = "  ";

    private PrettyPrinter() {}

    public static String print(Program program) {
        StringBuilder sb = new StringBuilder();
        program.module().ifPresent(m -> {
            sb.append("module ").append(joinDots(m.path())).append("\n\n");
        });
        for (ImportDecl imp : program.imports()) {
            sb.append("import ").append(joinDots(imp.path())).append("\n");
        }
        if (!program.imports().isEmpty()) {
            sb.append("\n");
        }
        boolean first = true;
        for (TopLevelDecl d : program.declarations()) {
            if (!first) {
                sb.append("\n");
            }
            appendTopLevel(sb, d);
            first = false;
        }
        return sb.toString();
    }

    private static void appendTopLevel(StringBuilder sb, TopLevelDecl d) {
        switch (d) {
            case RuleDecl r -> appendBlock(sb, "rule", quote(r.name()), r.clauses());
            case ScheduleDecl s -> appendBlock(sb, "schedule", quote(s.name()), s.clauses());
            case PolicyDecl p -> appendBlock(sb, "policy", quote(p.name()), p.clauses());
            case WaterfallDecl w -> appendWaterfall(sb, w);
            case TypeExtensionDecl t -> appendTypeExtension(sb, t);
        }
    }

    private static void appendBlock(StringBuilder sb, String keyword, String name, List<RuleClause> clauses) {
        sb.append(keyword).append(' ').append(name).append(" {\n");
        for (RuleClause c : clauses) {
            sb.append(INDENT);
            appendClause(sb, c);
            sb.append('\n');
        }
        sb.append("}\n");
    }

    private static void appendWaterfall(StringBuilder sb, WaterfallDecl w) {
        sb.append("waterfall ").append(quote(w.name())).append(" {\n");
        for (WaterfallDecl.WaterfallBody b : w.body()) {
            sb.append(INDENT);
            if (b instanceof LetBinding lb) {
                appendLet(sb, lb);
            } else if (b instanceof Statement s) {
                appendStatement(sb, s);
            }
            sb.append('\n');
        }
        sb.append("}\n");
    }

    private static void appendTypeExtension(StringBuilder sb, TypeExtensionDecl t) {
        sb.append("type extension ").append(t.typeName())
                .append(" extends ").append(t.baseType()).append(" {\n");
        for (FieldDecl f : t.fields()) {
            sb.append(INDENT).append("field ").append(f.name())
                    .append(": ").append(f.type().name()).append('\n');
        }
        sb.append("}\n");
    }

    private static void appendClause(StringBuilder sb, RuleClause c) {
        switch (c) {
            case DescriptionClause d -> sb.append("description: ").append(quote(d.text()));
            case AppliesToClause a -> {
                sb.append("applies to: ");
                appendQualifiedRef(sb, a.selector());
            }
            case EffectiveClause e -> {
                sb.append("effective: ");
                appendPeriod(sb, e.period());
            }
            case LetBinding l -> appendLet(sb, l);
            case Statement s -> appendStatement(sb, s);
        }
    }

    private static void appendLet(StringBuilder sb, LetBinding l) {
        sb.append("let ").append(l.name()).append(" = ");
        appendExpression(sb, l.value(), false);
    }

    private static void appendStatement(StringBuilder sb, Statement s) {
        switch (s) {
            case AccrueStmt a -> {
                sb.append("accrue ");
                appendExpression(sb, a.rate(), true);
                sb.append(" on ");
                appendExpression(sb, a.basis(), true);
                sb.append(" using ");
                appendDayCount(sb, a.dayCount());
            }
            case AllocateStmt al -> {
                sb.append("allocate ");
                appendExpression(sb, al.amount(), true);
                sb.append(" across ");
                appendExpression(sb, al.target(), true);
                switch (al.method()) {
                    case AllocationMethod.ProRata pr -> {
                        sb.append(" by ");
                        appendExpression(sb, pr.weight(), true);
                    }
                    case AllocationMethod.Equally e -> sb.append(" equally");
                }
            }
            case DistributeStmt d -> {
                sb.append("distribute ");
                appendExpression(sb, d.amount(), true);
                sb.append(" through waterfall ").append(quote(d.waterfallName()));
            }
            case PostStmt p -> {
                sb.append("post");
                p.subject().ifPresent(e -> {
                    sb.append(' ');
                    appendExpression(sb, e, true);
                });
                sb.append(" to ");
                appendQualifiedRef(sb, p.target());
                p.narrative().ifPresent(n -> sb.append(" with narrative ").append(quote(n)));
            }
            case PublishStmt p -> {
                sb.append("publish ");
                appendExpression(sb, p.subject(), true);
                p.asOf().ifPresent(d -> {
                    sb.append(" as of ");
                    appendDate(sb, d);
                });
            }
            case WhenStmt w -> {
                sb.append("when ");
                appendExpression(sb, w.condition(), true);
                sb.append(" then ");
                appendStatement(sb, w.thenBranch());
                w.elseBranch().ifPresent(eb -> {
                    sb.append(" else ");
                    appendStatement(sb, eb);
                });
            }
        }
    }

    // --- Expressions ---------------------------------------------------------

    private static void appendExpression(StringBuilder sb, Expression e, boolean asSubExpr) {
        boolean wrap = asSubExpr && !isAtomic(e);
        if (wrap) sb.append('(');
        switch (e) {
            case BinaryOpExpr b -> {
                appendExpression(sb, b.left(), true);
                sb.append(' ').append(b.op().symbol()).append(' ');
                appendExpression(sb, b.right(), true);
            }
            case AsOfExpr a -> {
                appendExpression(sb, a.expression(), true);
                sb.append(" as of ");
                appendDate(sb, a.date());
            }
            case AtBoundaryExpr a -> {
                appendExpression(sb, a.expression(), true);
                sb.append(a.boundary() == AtBoundaryExpr.Boundary.START ? " at start of " : " at end of ");
                appendPeriod(sb, a.period());
            }
            case OverExpr o -> {
                appendExpression(sb, o.expression(), true);
                sb.append(" over ");
                appendPeriod(sb, o.period());
                o.dayCount().ifPresent(dc -> {
                    sb.append(" using ");
                    appendDayCount(sb, dc);
                });
            }
            case PerAnnumExpr p -> {
                appendExpression(sb, p.expression(), true);
                sb.append(" per annum");
            }
            case NotExpr n -> {
                sb.append("not ");
                appendExpression(sb, n.expression(), true);
            }
            case FunctionCallExpr fc -> appendFunctionCall(sb, fc);
            case AggregationCall.SumOf so -> {
                sb.append("sum of ");
                appendExpression(sb, so.source(), true);
                so.by().ifPresent(byExpr -> {
                    sb.append(" by ");
                    appendExpression(sb, byExpr, true);
                });
            }
            case AggregationCall.WeightedAverage wa -> {
                sb.append("weighted average ");
                appendExpression(sb, wa.source(), true);
                sb.append(" weighted by ");
                appendExpression(sb, wa.weight(), true);
            }
            case Literal l -> appendLiteral(sb, l);
            case NameRef nr -> appendQualifiedRef(sb, nr.ref());
        }
        if (wrap) sb.append(')');
    }

    private static boolean isAtomic(Expression e) {
        return e instanceof Literal || e instanceof NameRef || e instanceof FunctionCallExpr;
    }

    private static void appendFunctionCall(StringBuilder sb, FunctionCallExpr fc) {
        sb.append(fc.name()).append('(');
        for (int i = 0; i < fc.arguments().size(); i++) {
            if (i > 0) sb.append(", ");
            appendExpression(sb, fc.arguments().get(i), false);
        }
        sb.append(')');
    }

    // --- Literals ------------------------------------------------------------

    private static void appendLiteral(StringBuilder sb, Literal l) {
        switch (l) {
            case Literal.MoneyLit m -> sb.append(formatMoney(m.value()));
            case Literal.DateLit d -> sb.append(d.value().toString());
            case Literal.PercentLit p -> sb.append(stripZeros(p.value().asPercent())).append('%');
            case Literal.BpsLit b -> sb.append(stripZeros(b.bps())).append(" bps");
            case Literal.DayCountLit dc -> sb.append(dc.value().code());
            case Literal.NumberLit n -> sb.append(stripZeros(n.value()));
            case Literal.StringLit s -> sb.append(quote(s.value()));
        }
    }

    private static String formatMoney(Money m) {
        return m.currency().getCurrencyCode() + " " + m.amount().toPlainString();
    }

    private static String stripZeros(java.math.BigDecimal v) {
        java.math.BigDecimal stripped = v.stripTrailingZeros();
        if (stripped.scale() < 0) {
            stripped = stripped.setScale(0, java.math.RoundingMode.UNNECESSARY);
        }
        return stripped.toPlainString();
    }

    // --- Periods / Dates / DayCounts -----------------------------------------

    private static void appendPeriod(StringBuilder sb, PeriodExpr p) {
        switch (p) {
            case PeriodExpr.ExplicitFromTo ft -> {
                sb.append("from ");
                appendDate(sb, ft.start());
                ft.end().ifPresent(e -> {
                    sb.append(" to ");
                    appendDate(sb, e);
                });
            }
            case PeriodExpr.FromInception fi -> sb.append("from inception");
            case PeriodExpr.NamedOrPhrasal np -> appendQualifiedRef(sb, np.name());
        }
    }

    private static void appendDate(StringBuilder sb, DateExpr d) {
        switch (d) {
            case DateExpr.Literal l -> sb.append(l.value().toString());
            case DateExpr.Inception i -> sb.append("inception");
            case DateExpr.Phrasal p -> appendQualifiedRef(sb, p.ref());
        }
    }

    private static void appendDayCount(StringBuilder sb, DayCountExpr dc) {
        switch (dc) {
            case DayCountExpr.Literal l -> sb.append(l.value().code());
            case DayCountExpr.Reference r -> appendQualifiedRef(sb, r.ref());
        }
    }

    // --- Refs ----------------------------------------------------------------

    private static void appendQualifiedRef(StringBuilder sb, QualifiedRef r) {
        for (int i = 0; i < r.phrases().size(); i++) {
            if (i > 0) sb.append(" of ");
            appendNounPhrase(sb, r.phrases().get(i));
        }
    }

    private static void appendNounPhrase(StringBuilder sb, NounPhrase np) {
        for (int i = 0; i < np.atoms().size(); i++) {
            if (i > 0) sb.append(' ');
            NounAtom a = np.atoms().get(i);
            switch (a) {
                case NounAtom.Ident id -> sb.append(id.text());
                case NounAtom.Quoted q -> sb.append(quote(q.value()));
                case NounAtom.Number n -> sb.append(n.text());
            }
        }
    }

    // --- Helpers -------------------------------------------------------------

    private static String quote(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 2);
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' || c == '\\') {
                sb.append('\\');
            }
            sb.append(c);
        }
        sb.append('"');
        return sb.toString();
    }

    private static String joinDots(List<String> parts) {
        return String.join(".", parts);
    }
}
