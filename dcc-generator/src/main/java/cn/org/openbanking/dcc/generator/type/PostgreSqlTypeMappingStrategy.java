package cn.org.openbanking.dcc.generator.type;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * PostgreSQL type mapping; the default dialect when {@code dcc.database.dialect} is
 * unset. Physical types pass through unchanged unless overridden under
 * {@code dcc.type-mapping.dialects.postgresql.*}.
 */
@Component
@ConditionalOnProperty(name = "dcc.database.dialect", havingValue = "postgresql", matchIfMissing = true)
public class PostgreSqlTypeMappingStrategy extends AbstractTypeMappingStrategy {

    public PostgreSqlTypeMappingStrategy(TypeMappingProperties properties) {
        super(properties);
    }

    @Override
    public String dialect() {
        return "postgresql";
    }
}
