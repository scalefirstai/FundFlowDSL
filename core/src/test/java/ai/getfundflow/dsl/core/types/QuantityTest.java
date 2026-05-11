package ai.getfundflow.dsl.core.types;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class QuantityTest {

    @Test
    void plusSameUnitAdds() {
        Quantity a = Quantity.ofShares(new BigDecimal("100"));
        Quantity b = Quantity.ofShares(new BigDecimal("250"));
        assertThat(a.plus(b).value()).isEqualByComparingTo("350");
    }

    @Test
    void plusDifferentUnitsThrows() {
        Quantity shares = Quantity.ofShares(new BigDecimal("100"));
        Quantity contracts = Quantity.of(new BigDecimal("100"), Contracts.INSTANCE);
        assertThatThrownBy(() -> shares.plus(contracts))
                .isInstanceOf(UnitMismatchException.class);
    }

    @Test
    void plusCustomUnitsByName() {
        Quantity a = Quantity.of(new BigDecimal("10"), new Custom("widgets"));
        Quantity b = Quantity.of(new BigDecimal("5"), new Custom("widgets"));
        assertThat(a.plus(b).value()).isEqualByComparingTo("15");
    }

    @Test
    void plusDifferentCustomUnitsThrows() {
        Quantity widgets = Quantity.of(new BigDecimal("10"), new Custom("widgets"));
        Quantity gizmos = Quantity.of(new BigDecimal("10"), new Custom("gizmos"));
        assertThatThrownBy(() -> widgets.plus(gizmos))
                .isInstanceOf(UnitMismatchException.class);
    }

    @Test
    void multiplyByScalar() {
        Quantity q = Quantity.ofShares(new BigDecimal("100")).multiply(new BigDecimal("2.5"));
        assertThat(q.value()).isEqualByComparingTo("250");
    }

    @Test
    void valueEqualityForSingletons() {
        assertThat(Shares.INSTANCE).isEqualTo(new Shares());
        assertThat(Contracts.INSTANCE).isEqualTo(new Contracts());
    }

    @Test
    void customUnitBlankNameRejected() {
        assertThatThrownBy(() -> new Custom(" "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
