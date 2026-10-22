package cn.org.openbanking.dcc.core.id;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

class IdRuleRegistryTest {

    @Test
    void exposesTheStockRules() {
        IdRuleRegistry registry = new IdRuleRegistry(new IdProperties());

        assertThat(registry.require(IdRules.TENANT_CODE).prefix()).isEqualTo("T");
        assertThat(registry.require(IdRules.TENANT_CODE).sequence().maxValue()).isEqualTo(999);
        assertThat(registry.require(CodeType.DATA_STANDARD.ruleCode()).parts())
                .extracting(IdPart::type)
                .containsExactly(PartType.TYPE, PartType.TENANT, PartType.APPLICATION, PartType.SEQUENCE);
    }

    @Test
    void aConfiguredRuleReplacesTheDefaultOfTheSameCode() {
        IdProperties properties = new IdProperties();
        IdProperties.Rule rule = new IdProperties.Rule();
        rule.setPrefix("STD");
        rule.setSeparator("-");
        rule.setMaxLength(64);
        IdProperties.Part type = new IdProperties.Part();
        type.setType("TYPE");
        IdProperties.Part tenant = new IdProperties.Part();
        tenant.setType("TENANT");
        tenant.setWidth(3);
        IdProperties.Part sequence = new IdProperties.Part();
        sequence.setType("SEQUENCE");
        sequence.setWidth(4);
        rule.setParts(List.of(type, tenant, sequence));
        IdProperties.Sequence sequenceSpec = new IdProperties.Sequence();
        sequenceSpec.setKey("custom:{type}:{tenant}");
        sequenceSpec.setMaxValue(9999L);
        rule.setSequence(sequenceSpec);
        properties.getRules().put(CodeType.DATA_STANDARD.ruleCode(), rule);

        IdRule effective = new IdRuleRegistry(properties).require(CodeType.DATA_STANDARD.ruleCode());

        assertThat(effective.prefix()).isEqualTo("STD");
        assertThat(effective.separator()).isEqualTo("-");
        assertThat(effective.sequence().key()).isEqualTo("custom:{type}:{tenant}");
    }

    @Test
    void rejectsAMaxValueThatDoesNotFitItsWidth() {
        IdProperties properties = new IdProperties();
        IdProperties.Rule rule = new IdProperties.Rule();
        IdProperties.Part sequence = new IdProperties.Part();
        sequence.setType("SEQUENCE");
        sequence.setWidth(2);
        rule.setParts(List.of(sequence));
        IdProperties.Sequence sequenceSpec = new IdProperties.Sequence();
        sequenceSpec.setKey("too-wide");
        sequenceSpec.setMaxValue(999L);
        rule.setSequence(sequenceSpec);
        properties.getRules().put("TOO_WIDE", rule);

        assertThatThrownBy(() -> new IdRuleRegistry(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not fit");
    }

    @Test
    void rejectsAnUnknownSequenceKeyPlaceholder() {
        IdProperties properties = new IdProperties();
        IdProperties.Rule rule = new IdProperties.Rule();
        IdProperties.Part sequence = new IdProperties.Part();
        sequence.setType("SEQUENCE");
        sequence.setWidth(3);
        rule.setParts(List.of(sequence));
        IdProperties.Sequence sequenceSpec = new IdProperties.Sequence();
        sequenceSpec.setKey("bad:{unknown}");
        sequenceSpec.setMaxValue(999L);
        rule.setSequence(sequenceSpec);
        properties.getRules().put("BAD_KEY", rule);

        assertThatThrownBy(() -> new IdRuleRegistry(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("placeholder");
    }
}
