package cn.org.openbanking.dcc.generator;

import cn.org.openbanking.dcc.generator.template.GeneratorProperties;
import cn.org.openbanking.dcc.generator.template.GeneratorTemplates;
import cn.org.openbanking.dcc.generator.template.TemplateRenderer;
import cn.org.openbanking.dcc.generator.type.PostgreSqlTypeMappingStrategy;
import cn.org.openbanking.dcc.generator.type.TypeMappingProperties;
import cn.org.openbanking.dcc.generator.type.TypeMappingStrategy;

/** Convenience factory so generator unit tests build real collaborators over the defaults. */
public final class TestTemplates {

    private TestTemplates() {
    }

    public static GeneratorProperties properties() {
        return new GeneratorProperties();
    }

    public static TemplateRenderer renderer() {
        return GeneratorTemplates.create(properties());
    }

    public static TypeMappingStrategy strategy() {
        return new PostgreSqlTypeMappingStrategy(new TypeMappingProperties());
    }
}
