package ai.getfundflow.dsl.parser;

import ai.getfundflow.dsl.ast.*;
import ai.getfundflow.dsl.ast.AggregationCall.SumOf;
import ai.getfundflow.dsl.ast.AggregationCall.WeightedAverage;
import ai.getfundflow.dsl.ast.AllocationMethod.Equally;
import ai.getfundflow.dsl.ast.AllocationMethod.ProRata;
import ai.getfundflow.dsl.ast.AtBoundaryExpr.Boundary;
import ai.getfundflow.dsl.ast.BinaryOpExpr.BinaryOp;
import ai.getfundflow.dsl.ast.NounPhrase.NounAtom;
import ai.getfundflow.dsl.core.types.Actual360;
import ai.getfundflow.dsl.core.types.Actual365;
import ai.getfundflow.dsl.core.types.ActualActual;
import ai.getfundflow.dsl.core.types.DayCount;
import ai.getfundflow.dsl.core.types.Money;
import ai.getfundflow.dsl.core.types.Percentage;
import ai.getfundflow.dsl.core.types.Thirty360;
import ai.getfundflow.dsl.parser.gen.FundFlowLexer;
import ai.getfundflow.dsl.parser.gen.FundFlowParser;
import ai.getfundflow.dsl.parser.gen.FundFlowParserBaseVisitor;
import ai.getfundflow.dsl.semantic.SourceLocation;
import ai.getfundflow.dsl.semantic.SourceMap;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.TerminalNode;

public final class AstBuilder extends FundFlowParserBaseVisitor<Object> {

    private final SourceMap sourceMap = new SourceMap();
    private String fileName = "<source>";

    public Program build(FundFlowParser.ProgramContext ctx) {
        return (Program) visit(ctx);
    }

    public Program build(FundFlowParser.ProgramContext ctx, String fileName) {
        this.fileName = fileName;
        return build(ctx);
    }

    public SourceMap sourceMap() {
        return sourceMap;
    }

    private <T> T track(T node, ParserRuleContext ctx) {
        if (node != null && ctx != null && ctx.getStart() != null) {
            sourceMap.put(node, locationOf(ctx));
        }
        return node;
    }

    private SourceLocation locationOf(ParserRuleContext ctx) {
        Token start = ctx.getStart();
        Token stop = ctx.getStop() == null ? start : ctx.getStop();
        int length = stop.getStopIndex() - start.getStartIndex() + 1;
        if (length <= 0) length = start.getText() == null ? 1 : start.getText().length();
        return new SourceLocation(fileName, start.getLine(), start.getCharPositionInLine() + 1, length);
    }

    @Override
    public Program visitProgram(FundFlowParser.ProgramContext ctx) {
        Optional<ModuleDecl> module = ctx.moduleDecl() == null
                ? Optional.empty()
                : Optional.of((ModuleDecl) visit(ctx.moduleDecl()));
        List<ImportDecl> imports = ctx.importDecl().stream()
                .map(c -> (ImportDecl) visit(c))
                .toList();
        List<TopLevelDecl> decls = ctx.topLevelDecl().stream()
                .map(c -> (TopLevelDecl) visit(c))
                .toList();
        return track(new Program(module, imports, decls), ctx);
    }

    @Override
    public ModuleDecl visitModuleDecl(FundFlowParser.ModuleDeclContext ctx) {
        return track(new ModuleDecl(modulePath(ctx.modulePath())), ctx);
    }

    @Override
    public ImportDecl visitImportDecl(FundFlowParser.ImportDeclContext ctx) {
        return track(new ImportDecl(modulePath(ctx.modulePath())), ctx);
    }

    private List<String> modulePath(FundFlowParser.ModulePathContext ctx) {
        return ctx.IDENT().stream().map(TerminalNode::getText).toList();
    }

    @Override
    public Object visitTopLevelDecl(FundFlowParser.TopLevelDeclContext ctx) {
        return visit(ctx.getChild(0));
    }

    // --- Rule / Schedule / Waterfall / Policy / TypeExtension ---------------

    @Override
    public RuleDecl visitRuleDecl(FundFlowParser.RuleDeclContext ctx) {
        String name = unquote(ctx.name.getText());
        List<RuleClause> clauses = ctx.ruleClause().stream()
                .map(c -> (RuleClause) visit(c))
                .toList();
        return track(new RuleDecl(name, clauses), ctx);
    }

    @Override
    public ScheduleDecl visitScheduleDecl(FundFlowParser.ScheduleDeclContext ctx) {
        String name = unquote(ctx.name.getText());
        List<RuleClause> clauses = ctx.ruleClause().stream()
                .map(c -> (RuleClause) visit(c))
                .toList();
        return track(new ScheduleDecl(name, clauses), ctx);
    }

    @Override
    public WaterfallDecl visitWaterfallDecl(FundFlowParser.WaterfallDeclContext ctx) {
        String name = unquote(ctx.name.getText());
        List<WaterfallDecl.WaterfallBody> body = ctx.waterfallBody().stream()
                .map(c -> (WaterfallDecl.WaterfallBody) visit(c))
                .toList();
        return track(new WaterfallDecl(name, body), ctx);
    }

    @Override
    public Object visitWaterfallBody(FundFlowParser.WaterfallBodyContext ctx) {
        return visit(ctx.getChild(0));
    }

    @Override
    public PolicyDecl visitPolicyDecl(FundFlowParser.PolicyDeclContext ctx) {
        String name = unquote(ctx.name.getText());
        List<RuleClause> clauses = ctx.ruleClause().stream()
                .map(c -> (RuleClause) visit(c))
                .toList();
        return track(new PolicyDecl(name, clauses), ctx);
    }

    @Override
    public TypeExtensionDecl visitTypeDecl(FundFlowParser.TypeDeclContext ctx) {
        List<FieldDecl> fields = ctx.fieldDecl().stream()
                .map(this::visitFieldDecl)
                .toList();
        return track(new TypeExtensionDecl(ctx.typeName.getText(), ctx.baseType.getText(), fields), ctx);
    }

    @Override
    public FieldDecl visitFieldDecl(FundFlowParser.FieldDeclContext ctx) {
        return track(new FieldDecl(ctx.name.getText(), new TypeRef(ctx.typeRef().IDENT().getText())), ctx);
    }

    // --- Rule clauses --------------------------------------------------------

    @Override
    public Object visitRuleClause(FundFlowParser.RuleClauseContext ctx) {
        return visit(ctx.getChild(0));
    }

    @Override
    public DescriptionClause visitDescriptionClause(FundFlowParser.DescriptionClauseContext ctx) {
        return track(new DescriptionClause(unquote(ctx.STRING().getText())), ctx);
    }

    @Override
    public AppliesToClause visitAppliesToClause(FundFlowParser.AppliesToClauseContext ctx) {
        return track(new AppliesToClause(qualifiedRef(ctx.selectorPhrase().qualifiedRef())), ctx);
    }

    @Override
    public EffectiveClause visitEffectiveClause(FundFlowParser.EffectiveClauseContext ctx) {
        return track(new EffectiveClause((PeriodExpr) visit(ctx.periodExpr())), ctx);
    }

    @Override
    public LetBinding visitLetBinding(FundFlowParser.LetBindingContext ctx) {
        return track(new LetBinding(ctx.name.getText(), expression(ctx.expression())), ctx);
    }

    // --- Statements ----------------------------------------------------------

    @Override
    public Object visitStatement(FundFlowParser.StatementContext ctx) {
        return visit(ctx.getChild(0));
    }

    @Override
    public AccrueStmt visitAccrueStmt(FundFlowParser.AccrueStmtContext ctx) {
        return track(new AccrueStmt(
                expression(ctx.rate),
                expression(ctx.basis),
                (DayCountExpr) visit(ctx.dayCount)), ctx);
    }

    @Override
    public AllocateStmt visitAllocateStmt(FundFlowParser.AllocateStmtContext ctx) {
        return track(new AllocateStmt(
                expression(ctx.amount),
                expression(ctx.targetSet),
                (AllocationMethod) visit(ctx.allocationMethod())), ctx);
    }

    @Override
    public AllocationMethod visitAllocationMethod(FundFlowParser.AllocationMethodContext ctx) {
        if (ctx.EQUALLY() != null) {
            return Equally.INSTANCE;
        }
        return track(new ProRata(expression(ctx.weight)), ctx);
    }

    @Override
    public DistributeStmt visitDistributeStmt(FundFlowParser.DistributeStmtContext ctx) {
        return track(new DistributeStmt(expression(ctx.amount), unquote(ctx.waterfallName.getText())), ctx);
    }

    @Override
    public PostStmt visitPostStmt(FundFlowParser.PostStmtContext ctx) {
        Optional<Expression> subject = ctx.subject == null
                ? Optional.empty()
                : Optional.of(expression(ctx.subject));
        QualifiedRef target = qualifiedRef(ctx.target.qualifiedRef());
        Optional<String> narrative = ctx.narrative == null
                ? Optional.empty()
                : Optional.of(unquote(ctx.narrative.getText()));
        return track(new PostStmt(subject, target, narrative), ctx);
    }

    @Override
    public PublishStmt visitPublishStmt(FundFlowParser.PublishStmtContext ctx) {
        Expression subject = expression(ctx.subject);
        Optional<DateExpr> asOf = ctx.AS_OF() == null
                ? Optional.empty()
                : Optional.of((DateExpr) visit(ctx.dateExpr()));
        return track(new PublishStmt(subject, asOf), ctx);
    }

    @Override
    public WhenStmt visitWhenStmt(FundFlowParser.WhenStmtContext ctx) {
        Expression cond = expression(ctx.cond);
        Statement thenBranch = (Statement) visit(ctx.thenBranch);
        Optional<Statement> elseBranch = ctx.elseBranch == null
                ? Optional.empty()
                : Optional.of((Statement) visit(ctx.elseBranch));
        return track(new WhenStmt(cond, thenBranch, elseBranch), ctx);
    }

    // --- Periods / Dates / DayCounts -----------------------------------------

    @Override
    public PeriodExpr visitExplicitFromTo(FundFlowParser.ExplicitFromToContext ctx) {
        DateExpr start = (DateExpr) visit(ctx.dateExpr(0));
        Optional<DateExpr> end = ctx.dateExpr().size() > 1
                ? Optional.of((DateExpr) visit(ctx.dateExpr(1)))
                : Optional.empty();
        return track(new PeriodExpr.ExplicitFromTo(start, end), ctx);
    }

    @Override
    public PeriodExpr visitFromInception(FundFlowParser.FromInceptionContext ctx) {
        return PeriodExpr.FromInception.INSTANCE;
    }

    @Override
    public PeriodExpr visitNamedOrPhrasalPeriod(FundFlowParser.NamedOrPhrasalPeriodContext ctx) {
        return track(new PeriodExpr.NamedOrPhrasal(qualifiedRef(ctx.qualifiedRef())), ctx);
    }

    @Override
    public DateExpr visitDateLiteralExpr(FundFlowParser.DateLiteralExprContext ctx) {
        return track(new DateExpr.Literal(LocalDate.parse(ctx.DATE_LITERAL().getText())), ctx);
    }

    @Override
    public DateExpr visitInceptionDateExpr(FundFlowParser.InceptionDateExprContext ctx) {
        return DateExpr.Inception.INSTANCE;
    }

    @Override
    public DateExpr visitPhrasalDateExpr(FundFlowParser.PhrasalDateExprContext ctx) {
        return track(new DateExpr.Phrasal(qualifiedRef(ctx.qualifiedRef())), ctx);
    }

    @Override
    public DayCountExpr visitDayCountLiteral(FundFlowParser.DayCountLiteralContext ctx) {
        return track(new DayCountExpr.Literal(parseDayCount(ctx.DAYCOUNT_LIT().getText())), ctx);
    }

    @Override
    public DayCountExpr visitDayCountReference(FundFlowParser.DayCountReferenceContext ctx) {
        return track(new DayCountExpr.Reference(qualifiedRef(ctx.qualifiedRef())), ctx);
    }

    // --- Expressions ---------------------------------------------------------

    @Override
    public Expression visitAsOfExpr(FundFlowParser.AsOfExprContext ctx) {
        return track(new AsOfExpr(expression(ctx.expression()), (DateExpr) visit(ctx.dateExpr())), ctx);
    }

    @Override
    public Expression visitAtBoundaryExpr(FundFlowParser.AtBoundaryExprContext ctx) {
        Boundary b = ctx.START() != null ? Boundary.START : Boundary.END;
        return track(new AtBoundaryExpr(
                expression(ctx.expression()),
                b,
                (PeriodExpr) visit(ctx.periodExpr())), ctx);
    }

    @Override
    public Expression visitOverExpr(FundFlowParser.OverExprContext ctx) {
        Optional<DayCountExpr> dc = ctx.inner == null
                ? Optional.empty()
                : Optional.of((DayCountExpr) visit(ctx.inner));
        return track(new OverExpr(expression(ctx.expression()), (PeriodExpr) visit(ctx.periodExpr()), dc), ctx);
    }

    @Override
    public Expression visitPerAnnumExpr(FundFlowParser.PerAnnumExprContext ctx) {
        return track(new PerAnnumExpr(expression(ctx.expression())), ctx);
    }

    @Override
    public Expression visitNotExpr(FundFlowParser.NotExprContext ctx) {
        return track(new NotExpr(expression(ctx.expression())), ctx);
    }

    @Override
    public Expression visitMulDivExpr(FundFlowParser.MulDivExprContext ctx) {
        BinaryOp op = ctx.op.getType() == FundFlowLexer.STAR ? BinaryOp.MUL : BinaryOp.DIV;
        return track(new BinaryOpExpr(op, expression(ctx.expression(0)), expression(ctx.expression(1))), ctx);
    }

    @Override
    public Expression visitAddSubExpr(FundFlowParser.AddSubExprContext ctx) {
        BinaryOp op = ctx.op.getType() == FundFlowLexer.PLUS ? BinaryOp.ADD : BinaryOp.SUB;
        return track(new BinaryOpExpr(op, expression(ctx.expression(0)), expression(ctx.expression(1))), ctx);
    }

    @Override
    public Expression visitCompareExpr(FundFlowParser.CompareExprContext ctx) {
        BinaryOp op = switch (ctx.op.getType()) {
            case FundFlowLexer.LT -> BinaryOp.LT;
            case FundFlowLexer.LE -> BinaryOp.LE;
            case FundFlowLexer.GT -> BinaryOp.GT;
            case FundFlowLexer.GE -> BinaryOp.GE;
            case FundFlowLexer.EQEQ -> BinaryOp.EQ;
            case FundFlowLexer.NEQ -> BinaryOp.NEQ;
            default -> throw new IllegalStateException("unknown compare op: " + ctx.op.getText());
        };
        return track(new BinaryOpExpr(op, expression(ctx.expression(0)), expression(ctx.expression(1))), ctx);
    }

    @Override
    public Expression visitAndExpr(FundFlowParser.AndExprContext ctx) {
        return track(new BinaryOpExpr(BinaryOp.AND, expression(ctx.expression(0)), expression(ctx.expression(1))), ctx);
    }

    @Override
    public Expression visitOrExpr(FundFlowParser.OrExprContext ctx) {
        return track(new BinaryOpExpr(BinaryOp.OR, expression(ctx.expression(0)), expression(ctx.expression(1))), ctx);
    }

    @Override
    public Expression visitFuncCallExpr(FundFlowParser.FuncCallExprContext ctx) {
        return (Expression) visit(ctx.functionCall());
    }

    @Override
    public Expression visitFunctionCall(FundFlowParser.FunctionCallContext ctx) {
        String name = ctx.name.getText().toLowerCase(Locale.ROOT);
        List<Expression> args = ctx.expression().stream().map(this::expression).toList();
        return track(new FunctionCallExpr(name, args), ctx);
    }

    @Override
    public Expression visitAggCallExpr(FundFlowParser.AggCallExprContext ctx) {
        return (Expression) visit(ctx.aggregationCall());
    }

    @Override
    public Expression visitSumOfExpr(FundFlowParser.SumOfExprContext ctx) {
        Expression source = expression(ctx.expression(0));
        Optional<Expression> by = ctx.expression().size() > 1
                ? Optional.of(expression(ctx.expression(1)))
                : Optional.empty();
        return track(new SumOf(source, by), ctx);
    }

    @Override
    public Expression visitWeightedAvgExpr(FundFlowParser.WeightedAvgExprContext ctx) {
        return track(new WeightedAverage(expression(ctx.expression(0)), expression(ctx.expression(1))), ctx);
    }

    @Override
    public Expression visitLiteralExpr(FundFlowParser.LiteralExprContext ctx) {
        return (Expression) visit(ctx.literal());
    }

    @Override
    public Expression visitNameExpr(FundFlowParser.NameExprContext ctx) {
        return track(new NameRef(qualifiedRef(ctx.qualifiedRef())), ctx);
    }

    @Override
    public Expression visitParenExpr(FundFlowParser.ParenExprContext ctx) {
        // Parens collapse — the AST tree structure already encodes precedence.
        return expression(ctx.expression());
    }

    // --- Literals ------------------------------------------------------------

    @Override
    public Literal visitLiteral(FundFlowParser.LiteralContext ctx) {
        Literal lit;
        if (ctx.MONEY_LITERAL() != null) {
            lit = parseMoneyLiteral(ctx.MONEY_LITERAL().getText());
        } else if (ctx.DATE_LITERAL() != null) {
            lit = new Literal.DateLit(LocalDate.parse(ctx.DATE_LITERAL().getText()));
        } else if (ctx.PCT_LITERAL() != null) {
            String text = ctx.PCT_LITERAL().getText();
            BigDecimal pct = new BigDecimal(text.substring(0, text.length() - 1));
            lit = new Literal.PercentLit(Percentage.ofPercent(pct));
        } else if (ctx.BPS_LITERAL() != null) {
            String text = ctx.BPS_LITERAL().getText();
            int bpsIdx = text.toLowerCase(Locale.ROOT).indexOf("bps");
            BigDecimal value = new BigDecimal(text.substring(0, bpsIdx).trim());
            lit = new Literal.BpsLit(value);
        } else if (ctx.DAYCOUNT_LIT() != null) {
            lit = new Literal.DayCountLit(parseDayCount(ctx.DAYCOUNT_LIT().getText()));
        } else if (ctx.NUMBER() != null) {
            lit = new Literal.NumberLit(new BigDecimal(ctx.NUMBER().getText()));
        } else {
            lit = new Literal.StringLit(unquote(ctx.STRING().getText()));
        }
        return track(lit, ctx);
    }

    private Literal.MoneyLit parseMoneyLiteral(String text) {
        int firstSpace = -1;
        for (int i = 0; i < text.length(); i++) {
            if (Character.isWhitespace(text.charAt(i))) {
                firstSpace = i;
                break;
            }
        }
        String code = text.substring(0, firstSpace);
        String amount = text.substring(firstSpace).trim().replace(",", "").replace("_", "");
        Money money = Money.of(new BigDecimal(amount), Currency.getInstance(code));
        return new Literal.MoneyLit(money);
    }

    private DayCount parseDayCount(String text) {
        return switch (text.toLowerCase(Locale.ROOT)) {
            case "actual/360" -> Actual360.INSTANCE;
            case "actual/365" -> Actual365.INSTANCE;
            case "30/360" -> Thirty360.INSTANCE;
            case "actual/actual" -> ActualActual.INSTANCE;
            default -> throw new IllegalStateException("unknown day-count: " + text);
        };
    }

    // --- QualifiedRef / NounPhrase ------------------------------------------

    private QualifiedRef qualifiedRef(FundFlowParser.QualifiedRefContext ctx) {
        List<NounPhrase> phrases = ctx.nounPhrase().stream()
                .map(this::nounPhrase)
                .toList();
        return track(new QualifiedRef(phrases), ctx);
    }

    private NounPhrase nounPhrase(FundFlowParser.NounPhraseContext ctx) {
        List<NounAtom> atoms = new ArrayList<>();
        for (FundFlowParser.NounAtomContext a : ctx.nounAtom()) {
            if (a.IDENT() != null) {
                atoms.add(new NounAtom.Ident(a.IDENT().getText()));
            } else if (a.STRING() != null) {
                atoms.add(new NounAtom.Quoted(unquote(a.STRING().getText())));
            } else {
                atoms.add(new NounAtom.Number(a.NUMBER().getText()));
            }
        }
        return track(new NounPhrase(atoms), ctx);
    }

    // --- Helpers -------------------------------------------------------------

    private Expression expression(ParserRuleContext ctx) {
        return (Expression) visit(ctx);
    }

    private static String unquote(String quoted) {
        if (quoted.length() < 2 || quoted.charAt(0) != '"' || quoted.charAt(quoted.length() - 1) != '"') {
            return quoted;
        }
        String inner = quoted.substring(1, quoted.length() - 1);
        StringBuilder sb = new StringBuilder(inner.length());
        for (int i = 0; i < inner.length(); i++) {
            char c = inner.charAt(i);
            if (c == '\\' && i + 1 < inner.length()) {
                sb.append(inner.charAt(i + 1));
                i++;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
