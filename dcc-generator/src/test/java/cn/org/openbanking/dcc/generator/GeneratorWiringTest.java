package cn.org.openbanking.dcc.generator;

import static org.assertj.core.api.Assertions.assertThat;

import cn.org.openbanking.dcc.generator.bundle.JarBundleGenerator;
import cn.org.openbanking.dcc.generator.ddl.TableDdlGenerator;
import cn.org.openbanking.dcc.generator.dto.DtoGenerator;
import cn.org.openbanking.dcc.generator.openapi.OpenApiGenerator;
import cn.org.openbanking.dcc.generator.template.GeneratorConfiguration;
import cn.org.openbanking.dcc.generator.template.GeneratorProperties;
import cn.org.openbanking.dcc.generator.template.TemplateRenderer;
import cn.org.openbanking.dcc.generator.type.MySqlTypeMappingStrategy;
import cn.org.openbanking.dcc.generator.type.PostgreSqlTypeMappingStrategy;
import cn.org.openbanking.dcc.generator.type.TypeMappingProperties;
import cn.org.openbanking.dcc.generator.type.TypeMappingStrategy;
import cn.org.openbanking.dcc.generator.validation.ValidationCodeGenerator;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Proves the generators auto-wire: the {@link TemplateRenderer} bean, the selected
 * {@link TypeMappingStrategy} and every generator component are all created from one
 * context, exactly as the applications' component scan would.
 */
class GeneratorWiringTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(WiringConfig.class);

    @Test
    void generatorsWireWithRendererAndPostgresStrategyByDefault() {
        runner.run(context -> {
            assertThat(context.getBean(TemplateRenderer.class)).isNotNull();
            assertThat(context.getBean(TypeMappingStrategy.class).dialect()).isEqualTo("postgresql");
            assertThat(context.getBean(DtoGenerator.class)).isNotNull();
            assertThat(context.getBean(ValidationCodeGenerator.class)).isNotNull();
            assertThat(context.getBean(TableDdlGenerator.class)).isNotNull();
            assertThat(context.getBean(OpenApiGenerator.class)).isNotNull();
            assertThat(context.getBean(JarBundleGenerator.class)).isNotNull();
        });
    }

    @Test
    void mysqlStrategyIsSelectedByProperty() {
        runner.withPropertyValues("dcc.database.dialect=mysql")
                .run(context -> assertThat(context.getBean(TypeMappingStrategy.class).dialect())
                        .isEqualTo("mysql"));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({ GeneratorProperties.class, TypeMappingProperties.class })
    @Import({ GeneratorConfiguration.class,
            PostgreSqlTypeMappingStrategy.class, MySqlTypeMappingStrategy.class,
            DtoGenerator.class, ValidationCodeGenerator.class, TableDdlGenerator.class,
            OpenApiGenerator.class, JarBundleGenerator.class })
    static class WiringConfig {
    }
}
