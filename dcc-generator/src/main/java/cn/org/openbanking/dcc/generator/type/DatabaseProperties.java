package cn.org.openbanking.dcc.generator.type;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Database selection, bound from {@code dcc.database.*}. The chosen
 * {@link TypeMappingStrategy} is activated by {@code dcc.database.dialect}.
 *
 * <pre>
 * dcc:
 *   database:
 *     dialect: postgresql
 * </pre>
 */
@ConfigurationProperties(prefix = "dcc.database")
public class DatabaseProperties {

    /** Active SQL dialect; selects the {@link TypeMappingStrategy} implementation. */
    private String dialect = "postgresql";

    public String getDialect() {
        return dialect;
    }

    public void setDialect(String dialect) {
        this.dialect = dialect;
    }
}
