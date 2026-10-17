package cn.org.openbanking.dcc.generator.template;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Wires the standalone template renderer used by the generators. The
 * {@link GeneratorProperties} bean itself is registered by the applications'
 * {@code @ConfigurationPropertiesScan} over {@code cn.org.openbanking.dcc}.
 */
@Configuration(proxyBeanMethods = false)
public class GeneratorConfiguration {

    /**
     * The general code/SQL renderer. Marked {@link Primary} because it is the default
     * {@link TemplateRenderer} for the generators; a second, purpose-specific renderer
     * (e.g. the MCP prompt renderer, which uses a different template prefix/suffix) must
     * be injected by name.
     */
    @Bean
    @Primary
    public TemplateRenderer codegenTemplateRenderer(GeneratorProperties properties) {
        return GeneratorTemplates.create(properties);
    }
}
