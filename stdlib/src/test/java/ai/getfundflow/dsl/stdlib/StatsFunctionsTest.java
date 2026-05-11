package ai.getfundflow.dsl.stdlib;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

class StatsFunctionsTest {

    private static List<BigDecimal> nums(String... values) {
        return java.util.Arrays.stream(values).map(BigDecimal::new).toList();
    }

    @Test
    void count() {
        assertThat(StatsFunctions.count(List.of(1, 2, 3))).isEqualTo(3);
        assertThat(StatsFunctions.count(List.of())).isEqualTo(0);
    }

    @Test
    void sum() {
        assertThat(StatsFunctions.sum(nums("1.5", "2.5", "3"))).isEqualByComparingTo("7");
        assertThat(StatsFunctions.sum(List.of())).isEqualByComparingTo("0");
    }

    @Test
    void average() {
        assertThat(StatsFunctions.average(nums("1", "2", "3", "4", "5"))).isEqualByComparingTo("3");
        assertThat(StatsFunctions.average(nums("10", "20"))).isEqualByComparingTo("15");
    }

    @Test
    void averageEmptyThrows() {
        assertThatThrownBy(() -> StatsFunctions.average(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void medianOdd() {
        assertThat(StatsFunctions.median(nums("3", "1", "2"))).isEqualByComparingTo("2");
        assertThat(StatsFunctions.median(nums("5"))).isEqualByComparingTo("5");
    }

    @Test
    void medianEven() {
        assertThat(StatsFunctions.median(nums("1", "2", "3", "4"))).isEqualByComparingTo("2.5");
    }

    @Test
    void sampleVariance() {
        BigDecimal v = StatsFunctions.variance(nums("2", "4", "4", "4", "5", "5", "7", "9"));
        assertThat(v.doubleValue()).isCloseTo(4.571428, Offset.offset(1e-5));
    }

    @Test
    void populationVariance() {
        BigDecimal v = StatsFunctions.variancePopulation(nums("2", "4", "4", "4", "5", "5", "7", "9"));
        assertThat(v.doubleValue()).isCloseTo(4.0, Offset.offset(1e-9));
    }

    @Test
    void sampleStdev() {
        BigDecimal sd = StatsFunctions.stdev(nums("2", "4", "4", "4", "5", "5", "7", "9"));
        assertThat(sd.doubleValue()).isCloseTo(Math.sqrt(4.571428), Offset.offset(1e-5));
    }

    @Test
    void populationStdev() {
        BigDecimal sd = StatsFunctions.stdevPopulation(nums("2", "4", "4", "4", "5", "5", "7", "9"));
        assertThat(sd.doubleValue()).isCloseTo(2.0, Offset.offset(1e-9));
    }

    @Test
    void sampleVarianceNeedsTwo() {
        assertThatThrownBy(() -> StatsFunctions.variance(nums("5")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
