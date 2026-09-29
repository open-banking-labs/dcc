package cn.org.openbanking.dcc.mcp.prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import cn.org.openbanking.dcc.generator.template.GeneratorTemplates;
import cn.org.openbanking.dcc.generator.template.TemplateRenderer;

import org.junit.jupiter.api.Test;

class PromptCatalogTest {

    private final PromptProperties properties = new PromptProperties();
    private final TemplateRenderer renderer =
            GeneratorTemplates.create(PromptConfiguration.PROMPT_PREFIX, PromptConfiguration.PROMPT_SUFFIX,
                    properties.getDir());
    private final PromptCatalog catalog = new PromptCatalog(renderer, properties);

    @Test
    void rendersTheDefaultLocaleTemplateWithVariables() {
        String prompt = catalog.render("review-change", Map.of("modelName", "Account", "changes", "ADD balance"));

        assertThat(prompt).contains("# Review request: Account");
        assertThat(prompt).contains("ADD balance");
        assertThat(prompt).contains("Backward compatibility");
    }

    @Test
    void rendersTheLocalizedTemplateWhenAvailable() {
        String prompt = catalog.render("review-change", Locale.SIMPLIFIED_CHINESE,
                Map.of("modelName", "账户", "changes", "新增余额字段"));

        assertThat(prompt).contains("# 评审请求：账户");
        assertThat(prompt).contains("新增余额字段");
        assertThat(prompt).contains("向后兼容");
    }

    @Test
    void fallsBackToDefaultForUnsupportedLocale() {
        String prompt = catalog.render("review-change", Locale.FRENCH, Map.of("modelName", "X", "changes", ""));

        assertThat(prompt).contains("# Review request: X");
    }

    @Test
    void specialCharactersAreNotEscaped() {
        String prompt = catalog.render("review-change", Map.of("modelName", "M", "changes", "a<b>&c"));

        assertThat(prompt).contains("a<b>&c").doesNotContain("&lt;").doesNotContain("&amp;");
    }

    @Test
    void missingPromptFailsClearly() {
        Map<String, Object> model = new HashMap<>();

        assertThatThrownBy(() -> catalog.render("does-not-exist", model))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does-not-exist");
    }
}
