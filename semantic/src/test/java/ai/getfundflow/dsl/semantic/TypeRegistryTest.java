package ai.getfundflow.dsl.semantic;

import static org.assertj.core.api.Assertions.assertThat;

import ai.getfundflow.dsl.semantic.DslType.BusinessDateType;
import ai.getfundflow.dsl.semantic.DslType.MoneyType;
import ai.getfundflow.dsl.semantic.DslType.PercentageType;
import ai.getfundflow.dsl.semantic.DslType.PeriodType;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TypeRegistryTest {

    @Test
    void recognizesBuiltInBaseTypes() {
        TypeRegistry r = new TypeRegistry();
        assertThat(r.isBaseType("Fund")).isTrue();
        assertThat(r.isBaseType("fund")).isTrue();   // lower-case variant
        assertThat(r.isBaseType("Investor")).isTrue();
        assertThat(r.isBaseType("ShareClass")).isTrue();
        assertThat(r.isBaseType("Banana")).isFalse();
    }

    @Test
    void canonicalBaseNormalizesCasing() {
        TypeRegistry r = new TypeRegistry();
        assertThat(r.canonicalBase("fund")).contains("Fund");
        assertThat(r.canonicalBase("INVESTOR")).contains("Investor");
        assertThat(r.canonicalBase("Banana")).isEmpty();
    }

    @Test
    void resolvesBuiltInFieldTypes() {
        TypeRegistry r = new TypeRegistry();
        assertThat(r.resolveFieldType("Money").orElseThrow()).isInstanceOf(MoneyType.class);
        assertThat(r.resolveFieldType("Period").orElseThrow()).isInstanceOf(PeriodType.class);
        assertThat(r.resolveFieldType("Percentage").orElseThrow()).isInstanceOf(PercentageType.class);
        assertThat(r.resolveFieldType("Date").orElseThrow()).isInstanceOf(BusinessDateType.class);
        assertThat(r.resolveFieldType("Banana")).isEmpty();
    }

    @Test
    void registerExtensionAggregatesFieldsByBase() {
        TypeRegistry r = new TypeRegistry();
        Map<String, DslType> peFields = new LinkedHashMap<>();
        peFields.put("commitment_period", PeriodType.INSTANCE);
        peFields.put("gp_commitment", PercentageType.INSTANCE);
        r.registerExtension(new TypeRegistry.ExtensionInfo("PrivateEquityFund", "Fund", peFields));

        assertThat(r.resolveExtensionField("Fund", "commitment_period")).contains(PeriodType.INSTANCE);
        assertThat(r.resolveExtensionField("fund", "commitment_period")).contains(PeriodType.INSTANCE);
        assertThat(r.resolveExtensionField("Fund", "gp_commitment")).contains(PercentageType.INSTANCE);
        assertThat(r.resolveExtensionField("Fund", "nope")).isEmpty();
    }

    @Test
    void duplicateExtensionRegistrationReturnsFalse() {
        TypeRegistry r = new TypeRegistry();
        TypeRegistry.ExtensionInfo info = new TypeRegistry.ExtensionInfo(
                "X", "Fund", Map.of("a", PeriodType.INSTANCE));
        assertThat(r.registerExtension(info)).isTrue();
        assertThat(r.registerExtension(info)).isFalse();
    }
}
