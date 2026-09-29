package cn.org.openbanking.dcc.generator.template;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the standalone template renderer used by the generators. The
 * {@link GeneratorProperties} bean itself is registered by the applications'
 * {@code @ConfigurationPropertiesScan} over {@code cn.org.openbanking.dcc}.
 */
@Configuration(proxyBeanMethods = false)
public class GeneratorConfiguration {

    @Bean
    public TemplateRenderer codegenTemplateRenderer(GeneratorProperties properties) {
        return GeneratorTemplates.create(properties);
    }
}
