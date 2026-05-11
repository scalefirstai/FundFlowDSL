package ai.getfundflow.dsl.core.types;

import java.math.BigDecimal;
import java.util.Currency;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

class MoneyPropertyTest {

    private static final Currency USD = Currency.getInstance("USD");

    @Property
    boolean additionIsAssociativeWithinCurrency(
            @ForAll("usdMoney") Money a,
            @ForAll("usdMoney") Money b,
            @ForAll("usdMoney") Money c) {
        Money left = a.plus(b).plus(c);
        Money right = a.plus(b.plus(c));
        return left.amount().compareTo(right.amount()) == 0
                && left.currency().equals(right.currency());
    }

    @Property
    boolean additionIsCommutativeWithinCurrency(
            @ForAll("usdMoney") Money a,
            @ForAll("usdMoney") Money b) {
        return a.plus(b).amount().compareTo(b.plus(a).amount()) == 0;
    }

    @Property
    boolean negationIsInverseOfAddition(@ForAll("usdMoney") Money a) {
        Money result = a.plus(a.negate());
        return result.isZero();
    }

    @Provide
    Arbitrary<Money> usdMoney() {
        Arbitrary<BigDecimal> amounts = Arbitraries.bigDecimals()
                .between(new BigDecimal("-1000000"), new BigDecimal("1000000"))
                .ofScale(2);
        return amounts.map(amt -> Money.of(amt, USD));
    }
}
