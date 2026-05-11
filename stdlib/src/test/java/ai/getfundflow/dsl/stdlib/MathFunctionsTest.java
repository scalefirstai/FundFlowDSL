package ai.getfundflow.dsl.stdlib;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MathFunctionsTest {

    private static BigDecimal bd(String s) {
        return new BigDecimal(s);
    }

    @Test
    void abs() {
        assertThat(MathFunctions.abs(bd("-5.25"))).isEqualByComparingTo("5.25");
        assertThat(MathFunctions.abs(bd("5.25"))).isEqualByComparingTo("5.25");
        assertThat(MathFunctions.abs(bd("0"))).isEqualByComparingTo("0");
    }

    @Test
    void roundHalfEven() {
        assertThat(MathFunctions.round(bd("2.5"), 0)).isEqualByComparingTo("2");
        assertThat(MathFunctions.round(bd("3.5"), 0)).isEqualByComparingTo("4");
        assertThat(MathFunctions.round(bd("1.235"), 2)).isEqualByComparingTo("1.24");
    }

    @Test
    void ceilingFloor() {
        assertThat(MathFunctions.ceiling(bd("1.1"))).isEqualByComparingTo("2");
        assertThat(MathFunctions.ceiling(bd("-1.1"))).isEqualByComparingTo("-1");
        assertThat(MathFunctions.floor(bd("1.9"))).isEqualByComparingTo("1");
        assertThat(MathFunctions.floor(bd("-1.1"))).isEqualByComparingTo("-2");
    }

    @Test
    void truncate() {
        assertThat(MathFunctions.truncate(bd("1.9"))).isEqualByComparingTo("1");
        assertThat(MathFunctions.truncate(bd("-1.9"))).isEqualByComparingTo("-1");
    }

    @Test
    void mod() {
        assertThat(MathFunctions.mod(bd("10"), bd("3"))).isEqualByComparingTo("1");
        assertThat(MathFunctions.mod(bd("10.5"), bd("3"))).isEqualByComparingTo("1.5");
    }

    @Test
    void modByZeroThrows() {
        assertThatThrownBy(() -> MathFunctions.mod(bd("1"), bd("0")))
                .isInstanceOf(ArithmeticException.class);
    }

    @Test
    void powerInteger() {
        assertThat(MathFunctions.power(bd("2"), bd("10"))).isEqualByComparingTo("1024");
        assertThat(MathFunctions.power(bd("3"), bd("0"))).isEqualByComparingTo("1");
    }

    @Test
    void powerFractional() {
        BigDecimal r = MathFunctions.power(bd("4"), bd("0.5"));
        assertThat(r.doubleValue()).isCloseTo(2.0, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void sqrt() {
        assertThat(MathFunctions.sqrt(bd("4")).doubleValue()).isCloseTo(2.0, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(MathFunctions.sqrt(bd("0"))).isEqualByComparingTo("0");
    }

    @Test
    void sqrtNegativeThrows() {
        assertThatThrownBy(() -> MathFunctions.sqrt(bd("-1")))
                .isInstanceOf(ArithmeticException.class);
    }

    @Test
    void sign() {
        assertThat(MathFunctions.sign(bd("-5"))).isEqualTo(-1);
        assertThat(MathFunctions.sign(bd("0"))).isEqualTo(0);
        assertThat(MathFunctions.sign(bd("5"))).isEqualTo(1);
    }

    @Test
    void maxMinVariadic() {
        assertThat(MathFunctions.max(bd("0"), bd("3"), bd("-2"), bd("5"), bd("1")))
                .isEqualByComparingTo("5");
        assertThat(MathFunctions.min(bd("0"), bd("3"), bd("-2"), bd("5"), bd("1")))
                .isEqualByComparingTo("-2");
    }

    @Test
    void maxMinTwoArg() {
        assertThat(MathFunctions.max(bd("0"), bd("3"))).isEqualByComparingTo("3");
        assertThat(MathFunctions.min(bd("0"), bd("3"))).isEqualByComparingTo("0");
    }

    @Test
    void maxMinSingleton() {
        assertThat(MathFunctions.max(bd("42"))).isEqualByComparingTo("42");
        assertThat(MathFunctions.min(bd("42"))).isEqualByComparingTo("42");
    }
}
