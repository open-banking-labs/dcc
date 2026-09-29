package cn.org.openbanking.dcc.generator.type;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * MySQL type mapping, activated by {@code dcc.database.dialect=mysql}. Provided as the
 * reference for adding a dialect: one strategy class (this one) plus its optional
 * {@code dcc.type-mapping.dialects.mysql.*} overrides — no changes to the generators.
 */
@Component
@ConditionalOnProperty(name = "dcc.database.dialect", havingValue = "mysql")
public class MySqlTypeMappingStrategy extends AbstractTypeMappingStrategy {

    public MySqlTypeMappingStrategy(TypeMappingProperties properties) {
        super(properties);
    }

    @Override
    public String dialect() {
        return "mysql";
    }
}
