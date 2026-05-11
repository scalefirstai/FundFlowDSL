package ai.getfundflow.dsl.semantic;

import static org.assertj.core.api.Assertions.assertThat;

import ai.getfundflow.dsl.ast.BinaryOpExpr;
import ai.getfundflow.dsl.ast.BinaryOpExpr.BinaryOp;
import ai.getfundflow.dsl.ast.Expression;
import ai.getfundflow.dsl.ast.FunctionCallExpr;
import ai.getfundflow.dsl.ast.Literal;
import ai.getfundflow.dsl.ast.NameRef;
import ai.getfundflow.dsl.ast.NotExpr;
import ai.getfundflow.dsl.ast.NounPhrase;
import ai.getfundflow.dsl.ast.NounPhrase.NounAtom;
import ai.getfundflow.dsl.ast.QualifiedRef;
import ai.getfundflow.dsl.core.types.Money;
import ai.getfundflow.dsl.core.types.Percentage;
import ai.getfundflow.dsl.semantic.DslType.BigDecimalType;
import ai.getfundflow.dsl.semantic.DslType.BooleanType;
import ai.getfundflow.dsl.semantic.DslType.MoneyType;
import ai.getfundflow.dsl.semantic.DslType.PercentageType;
import ai.getfundflow.dsl.semantic.DslType.UnknownType;
import java.math.BigDecimal;
import java.util.Currency;
import java.util.List;
import org.junit.jupiter.api.Test;

class TypeCheckerTest {

    private static final Currency USD = Currency.getInstance("USD");
    private static final Currency EUR = Currency.getInstance("EUR");

    private TypeChecker checker;
    private Diagnostics diagnostics;

    private void freshChecker() {
        diagnostics = new Diagnostics();
        SymbolTable symbols = new SymbolTable();
        checker = new TypeChecker(symbols, diagnostics);
    }

    private static Expression money(String amount, Currency ccy) {
        return new Literal.MoneyLit(Money.of(new BigDecimal(amount), ccy));
    }

    private static Expression pct(String value) {
        return new Literal.PercentLit(Percentage.ofPercent(value));
    }

    private static Expression num(String value) {
        return new Literal.NumberLit(new BigDecimal(value));
    }

    private static Expression name(String identifier) {
        return new NameRef(new QualifiedRef(List.of(
                new NounPhrase(List.of(new NounAtom.Ident(identifier))))));
    }

    @Test
    void moneyPlusMoneySameCurrency() {
        freshChecker();
        BinaryOpExpr e = new BinaryOpExpr(BinaryOp.ADD, money("100", USD), money("50", USD));
        DslType t = checker.inferExpression(e);
        assertThat(t).isInstanceOf(MoneyType.class);
        assertThat(((MoneyType) t).currency()).isEqualTo(USD);
        assertThat(diagnostics.hasErrors()).isFalse();
    }

    @Test
    void moneyPlusMoneyDifferentCurrencyEmitsCurrencyMismatch() {
        freshChecker();
        BinaryOpExpr e = new BinaryOpExpr(BinaryOp.ADD, money("100", USD), money("50", EUR));
        checker.inferExpression(e);
        assertThat(diagnostics.errors())
                .extracting(d -> d.code())
                .contains(DiagnosticCode.CURRENCY_MISMATCH);
    }

    @Test
    void moneyTimesPercentageReturnsMoney() {
        freshChecker();
        BinaryOpExpr e = new BinaryOpExpr(BinaryOp.MUL, money("1000000", USD), pct("1.5"));
        DslType t = checker.inferExpression(e);
        assertThat(t).isInstanceOf(MoneyType.class);
        assertThat(diagnostics.hasErrors()).isFalse();
    }

    @Test
    void moneyTimesMoneyEmitsError() {
        freshChecker();
        BinaryOpExpr e = new BinaryOpExpr(BinaryOp.MUL, money("100", USD), money("50", USD));
        checker.inferExpression(e);
        assertThat(diagnostics.errors())
                .extracting(d -> d.code())
                .contains(DiagnosticCode.MONEY_MULTIPLY_MONEY);
    }

    @Test
    void percentagePlusPercentageOk() {
        freshChecker();
        BinaryOpExpr e = new BinaryOpExpr(BinaryOp.ADD, pct("1.5"), pct("0.5"));
        DslType t = checker.inferExpression(e);
        assertThat(t).isInstanceOf(PercentageType.class);
    }

    @Test
    void scalarTimesMoneyReturnsMoney() {
        freshChecker();
        BinaryOpExpr e = new BinaryOpExpr(BinaryOp.MUL, num("2.5"), money("100", USD));
        DslType t = checker.inferExpression(e);
        assertThat(t).isInstanceOf(MoneyType.class);
    }

    @Test
    void largeScalarOnMoneyEmitsWarning() {
        freshChecker();
        BinaryOpExpr e = new BinaryOpExpr(BinaryOp.MUL, money("100", USD), num("5000"));
        checker.inferExpression(e);
        assertThat(diagnostics.warnings())
                .extracting(d -> d.code())
                .contains(DiagnosticCode.LARGE_SCALAR_ON_MONEY);
    }

    @Test
    void moneyDivMoneyIsScalar() {
        freshChecker();
        BinaryOpExpr e = new BinaryOpExpr(BinaryOp.DIV, money("100", USD), money("4", USD));
        DslType t = checker.inferExpression(e);
        assertThat(t).isInstanceOf(BigDecimalType.class);
    }

    @Test
    void compareMoneySameCurrencyOk() {
        freshChecker();
        BinaryOpExpr e = new BinaryOpExpr(BinaryOp.LT, money("100", USD), money("200", USD));
        DslType t = checker.inferExpression(e);
        assertThat(t).isInstanceOf(BooleanType.class);
    }

    @Test
    void notRequiresBoolean() {
        freshChecker();
        NotExpr e = new NotExpr(money("100", USD));
        checker.inferExpression(e);
        assertThat(diagnostics.errors())
                .extracting(d -> d.code())
                .contains(DiagnosticCode.INVALID_OPERAND);
    }

    @Test
    void andOrRequireBoolean() {
        freshChecker();
        BinaryOpExpr e = new BinaryOpExpr(BinaryOp.AND, money("1", USD), money("2", USD));
        checker.inferExpression(e);
        assertThat(diagnostics.errors())
                .extracting(d -> d.code())
                .contains(DiagnosticCode.INVALID_OPERAND);
    }

    @Test
    void unknownFunctionEmitsError() {
        freshChecker();
        FunctionCallExpr fc = new FunctionCallExpr("not_a_function", List.of());
        checker.inferExpression(fc);
        assertThat(diagnostics.errors())
                .extracting(d -> d.code())
                .contains(DiagnosticCode.UNKNOWN_FUNCTION);
    }

    @Test
    void functionArityMismatch() {
        freshChecker();
        FunctionCallExpr fc = new FunctionCallExpr("abs", List.of(num("1"), num("2")));
        checker.inferExpression(fc);
        assertThat(diagnostics.errors())
                .extracting(d -> d.code())
                .contains(DiagnosticCode.FUNCTION_ARITY_MISMATCH);
    }

    @Test
    void functionMaxReturnsArgType() {
        freshChecker();
        FunctionCallExpr fc = new FunctionCallExpr("max", List.of(money("100", USD), money("200", USD)));
        DslType t = checker.inferExpression(fc);
        assertThat(t).isInstanceOf(MoneyType.class);
    }

    @Test
    void unresolvedSimpleNameEmitsDiagnostic() {
        freshChecker();
        DslType t = checker.inferExpression(name("undefined_var"));
        assertThat(t).isInstanceOf(UnknownType.class);
        assertThat(diagnostics.errors())
                .extracting(d -> d.code())
                .contains(DiagnosticCode.UNRESOLVED_BINDING);
    }
}
