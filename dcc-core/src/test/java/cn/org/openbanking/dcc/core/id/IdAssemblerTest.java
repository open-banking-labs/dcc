package cn.org.openbanking.dcc.core.id;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;

class IdAssemblerTest {

    private final IdAssembler assembler = new IdAssembler();

    @Test
    void assemblesPrefixPartsSeparatorAndSuffix() {
        IdRule rule = new IdRule("TEST", "P", "X", "-",
                List.of(IdPart.typePart(), IdPart.tenant(3), IdPart.application(3), IdPart.sequence(8)),
                new SequenceSpec(SequenceSpec.DB_SEGMENT, "test", 1, 99_999_999L), 64);

        String id = assembler.assemble(rule, new IdContext('S', 1L, 2, 3L, Instant.EPOCH));

        assertThat(id).isEqualTo("PS-001-002-00000003X");
    }

    @Test
    void padsAndUsesConfiguredRadix() {
        IdRule rule = new IdRule("RADIX", "", "", "",
                List.of(new IdPart(PartType.SEQUENCE, "", 4, 16, '0', null, null)),
                new SequenceSpec(SequenceSpec.DB_SEGMENT, "radix", 1, 65535), 16);

        assertThat(assembler.assemble(rule, new IdContext(null, null, null, 255L, Instant.EPOCH)))
                .isEqualTo("00FF");
    }

    @Test
    void datePartUsesConfiguredPatternAndZone() {
        IdRule rule = new IdRule("DATE", "", "", "",
                List.of(IdPart.date("yyyyMMdd", ZoneOffset.UTC)), null, 8);

        assertThat(assembler.assemble(rule, new IdContext(null, null, null, null, Instant.parse("2026-10-22T00:00:00Z"))))
                .isEqualTo("20261022");
    }

    @Test
    void refusesToTruncateAnOverwideValue() {
        IdRule rule = new IdRule("TIGHT", "", "", "",
                List.of(IdPart.sequence(2)), new SequenceSpec(SequenceSpec.DB_SEGMENT, "tight", 1, 999), 2);

        assertThatThrownBy(() -> assembler.assemble(rule,
                new IdContext(null, null, null, 1000L, Instant.EPOCH)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exceeds width");
    }
}
