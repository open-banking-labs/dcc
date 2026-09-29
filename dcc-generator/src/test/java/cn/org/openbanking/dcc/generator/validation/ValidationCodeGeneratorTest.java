package cn.org.openbanking.dcc.generator.validation;

import static org.assertj.core.api.Assertions.assertThat;

import cn.org.openbanking.dcc.core.standard.StandardContent;
import cn.org.openbanking.dcc.generator.TestTemplates;
import cn.org.openbanking.dcc.generator.source.GeneratedSource;

import org.junit.jupiter.api.Test;

class ValidationCodeGeneratorTest {

    private final ValidationCodeGenerator generator = new ValidationCodeGenerator(TestTemplates.renderer());

    @Test
    void generatesValidationClassWithConstraints() {
        StandardContent content =
                new StandardContent("账号", "d", "VARCHAR", 32, null, true, null, null, "[0-9]+", "123");

        GeneratedSource source = generator.generate("com.acme", "AcctnoValidation", "acctno", content);

        assertThat(source.fileName()).isEqualTo("com/acme/AcctnoValidation.java");
        assertThat(source.content()).contains("package com.acme;");
        assertThat(source.content()).contains("public final class AcctnoValidation");
        assertThat(source.content()).contains("STANDARD = \"acctno\"");
        assertThat(source.content()).contains("REQUIRED = true");
        assertThat(source.content()).contains("LENGTH = 32");
        assertThat(source.content()).contains("REGEX = \"[0-9]+\"");
        assertThat(source.content()).contains("public static String validate(String value)");
    }
}
