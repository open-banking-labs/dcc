package cn.org.openbanking.dcc.mcp.prompt;

import cn.org.openbanking.dcc.generator.template.GeneratorTemplates;
import cn.org.openbanking.dcc.generator.template.TemplateRenderer;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the prompt-template renderer. It reuses the generator's template engine
 * configuration (TEXT mode, no HTML escaping) but over the {@code mcp/prompts/}
 * classpath prefix and the {@code .md.tpl} suffix, so prompts are the same
 * Thymeleaf stack as the code/SQL templates.
 */
@Configuration(proxyBeanMethods = false)
public class PromptConfiguration {

    static final String PROMPT_PREFIX = "mcp/prompts/";
    static final String PROMPT_SUFFIX = ".md.tpl";

    @Bean
    public TemplateRenderer mcpPromptTemplateRenderer(PromptProperties properties) {
        return GeneratorTemplates.create(PROMPT_PREFIX, PROMPT_SUFFIX, properties.getDir());
    }
}
