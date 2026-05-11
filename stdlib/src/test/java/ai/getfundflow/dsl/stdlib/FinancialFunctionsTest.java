package ai.getfundflow.dsl.stdlib;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

class FinancialFunctionsTest {

    private static List<BigDecimal> nums(String... values) {
        return java.util.Arrays.stream(values).map(BigDecimal::new).toList();
    }

    private static BigDecimal bd(String s) {
        return new BigDecimal(s);
    }

    @Test
    void npvMatchesExcelConvention() {
        // Excel: NPV(10%, 100, 200, 300) = 100/1.1 + 200/1.21 + 300/1.331 = 481.5928...
        BigDecimal r = FinancialFunctions.npv(bd("0.10"), nums("100", "200", "300"));
        assertThat(r.doubleValue()).isCloseTo(481.59279, Offset.offset(1e-4));
    }

    @Test
    void npvWithNegativeInitial() {
        // Excel NPV: every cashflow is end-of-period (-1000/1.1 + 300/1.21 + 400/1.331 + 500/1.4641)
        BigDecimal r = FinancialFunctions.npv(bd("0.10"), nums("-1000", "300", "400", "500"));
        assertThat(r.doubleValue()).isCloseTo(-19.1244, Offset.offset(1e-3));
    }

    @Test
    void irrSimple() {
        // -100 at t=0, 110 at t=1 → IRR = 10%
        BigDecimal r = FinancialFunctions.irr(nums("-100", "110"));
        assertThat(r.doubleValue()).isCloseTo(0.10, Offset.offset(1e-6));
    }

    @Test
    void irrTypicalProject() {
        // -1000 at t=0, 300/400/500 in years 1-3 → IRR ≈ 8.9%
        BigDecimal r = FinancialFunctions.irr(nums("-1000", "300", "400", "500"));
        assertThat(r.doubleValue()).isCloseTo(0.0890, Offset.offset(1e-3));
    }

    @Test
    void irrNeedsTwoCashflows() {
        assertThatThrownBy(() -> FinancialFunctions.irr(nums("100")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void xnpvBasic() {
        List<BigDecimal> cf = nums("-10000", "2750", "4250", "3250", "2750");
        List<LocalDate> dates = List.of(
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 10, 30),
                LocalDate.of(2027, 2, 15),
                LocalDate.of(2027, 4, 1));
        BigDecimal r = FinancialFunctions.xnpv(bd("0.09"), cf, dates);
        // Excel reference is approximate (uses 365-day year-fraction); allow ±5
        assertThat(r.doubleValue()).isCloseTo(2086.65, Offset.offset(5.0));
    }

    @Test
    void xirrBasic() {
        List<BigDecimal> cf = nums("-10000", "2750", "4250", "3250", "2750");
        List<LocalDate> dates = List.of(
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 10, 30),
                LocalDate.of(2027, 2, 15),
                LocalDate.of(2027, 4, 1));
        BigDecimal r = FinancialFunctions.xirr(cf, dates);
        assertThat(r.doubleValue()).isCloseTo(0.3733, Offset.offset(0.01));
    }

    @Test
    void pvBasic() {
        // PV(5%, 10, -100) ≈ 772.17 (Excel convention: pmt sign flipped)
        BigDecimal r = FinancialFunctions.pv(bd("0.05"), 10, bd("-100"));
        assertThat(r.doubleValue()).isCloseTo(772.1735, Offset.offset(1e-3));
    }

    @Test
    void pvZeroRate() {
        BigDecimal r = FinancialFunctions.pv(BigDecimal.ZERO, 10, bd("-100"));
        assertThat(r).isEqualByComparingTo("1000");
    }

    @Test
    void fvBasic() {
        // FV(5%, 10, -100) ≈ 1257.79
        BigDecimal r = FinancialFunctions.fv(bd("0.05"), 10, bd("-100"));
        assertThat(r.doubleValue()).isCloseTo(1257.789, Offset.offset(1e-3));
    }

    @Test
    void fvZeroRate() {
        BigDecimal r = FinancialFunctions.fv(BigDecimal.ZERO, 10, bd("-100"));
        assertThat(r).isEqualByComparingTo("1000");
    }

    @Test
    void pmtBasic() {
        // PMT(5%/12, 360, 200000) ≈ -1073.64 (monthly mortgage)
        BigDecimal monthlyRate = bd("0.05").divide(bd("12"), java.math.MathContext.DECIMAL64);
        BigDecimal pmt = FinancialFunctions.pmt(monthlyRate, 360, bd("200000"));
        assertThat(pmt.doubleValue()).isCloseTo(-1073.6436, Offset.offset(1e-3));
    }

    @Test
    void pmtZeroRate() {
        BigDecimal pmt = FinancialFunctions.pmt(BigDecimal.ZERO, 12, bd("1200"));
        assertThat(pmt).isEqualByComparingTo("-100");
    }

    @Test
    void pmtRequiresPositiveNper() {
        assertThatThrownBy(() -> FinancialFunctions.pmt(bd("0.05"), 0, bd("100")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void pvFvRoundtrip() {
        BigDecimal rate = bd("0.06");
        int n = 5;
        BigDecimal pmt = bd("-100");
        BigDecimal pv = FinancialFunctions.pv(rate, n, pmt);
        BigDecimal fv = FinancialFunctions.fv(rate, n, pmt, pv);
        assertThat(fv.abs().doubleValue()).isCloseTo(0.0, Offset.offset(1e-6));
    }
}
