package cn.org.openbanking.dcc.generator.openapi;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import cn.org.openbanking.dcc.core.interfaceapi.content.InterfaceContent;
import cn.org.openbanking.dcc.core.interfaceapi.content.InterfaceField;
import cn.org.openbanking.dcc.generator.TestTemplates;

import org.junit.jupiter.api.Test;

class OpenApiGeneratorTest {

    private final OpenApiGenerator generator = new OpenApiGenerator(TestTemplates.renderer(), TestTemplates.strategy());

    @Test
    void generatesPathsSchemasAndMock() {
        InterfaceContent content = new InterfaceContent("转账", "payment", "/transfer", "d", "transfer",
                List.of(new InterfaceField("acctno", 1L, "acctno", "VARCHAR", 32, null, true, false, List.of())),
                List.of(new InterfaceField("status", null, null, "VARCHAR", 8, null, true, false, List.of())));

        String json = generator.generate("DCC", "1.0.0", List.of(new InterfaceSpec("IF0001", "转账", "/transfer", content)));

        assertThat(json).contains("\"openapi\": \"3.0.3\"");
        assertThat(json).contains("\"/transfer\"");
        assertThat(json).contains("\"operationId\": \"IF0001\"");
        assertThat(json).contains("IF0001Request");
        assertThat(json).contains("IF0001Response");
        assertThat(json).contains("\"x-mock\"");
    }
}
