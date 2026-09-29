package cn.org.openbanking.dcc.generator.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import cn.org.openbanking.dcc.core.interfaceapi.content.InterfaceContent;
import cn.org.openbanking.dcc.core.interfaceapi.content.InterfaceField;
import cn.org.openbanking.dcc.generator.TestTemplates;
import cn.org.openbanking.dcc.generator.source.GeneratedSource;

import org.junit.jupiter.api.Test;

class DtoGeneratorTest {

    private final DtoGenerator generator = new DtoGenerator(TestTemplates.renderer(), TestTemplates.properties(), TestTemplates.strategy());

    @Test
    void generatesRequestAndResponseRecords() {
        InterfaceContent content = new InterfaceContent("转账", "payment", "/transfer", "d", "transfer",
                List.of(new InterfaceField("acctno", 1L, "acctno", "VARCHAR", 32, null, true, false, List.of())),
                List.of(new InterfaceField("status", null, null, "VARCHAR", 8, null, true, false, List.of())));

        List<GeneratedSource> sources = generator.generate("com.acme", "IF0001", content);

        assertThat(sources).extracting(GeneratedSource::fileName)
                .contains("com/acme/IF0001Request.java", "com/acme/IF0001Response.java");
        GeneratedSource request = sources.stream()
                .filter(s -> s.fileName().endsWith("IF0001Request.java")).findFirst().orElseThrow();
        assertThat(request.content()).contains("public record IF0001Request(");
        assertThat(request.content()).contains("String acctno");
    }

    @Test
    void generatesNestedRecordForNestedFields() {
        InterfaceContent content = new InterfaceContent("x", null, null, null, null,
                List.of(new InterfaceField("payload", null, null, null, null, null, true, false,
                        List.of(new InterfaceField("amount", null, null, "NUMERIC", 18, 2, true, false, List.of())))),
                List.of());

        List<GeneratedSource> sources = generator.generate("com.acme", "IF2", content);

        assertThat(sources).extracting(GeneratedSource::fileName).contains("com/acme/PayloadDto.java");
        GeneratedSource request = sources.stream()
                .filter(s -> s.fileName().endsWith("IF2Request.java")).findFirst().orElseThrow();
        assertThat(request.content()).contains("PayloadDto payload");
    }
}
