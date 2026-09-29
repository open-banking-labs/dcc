package cn.org.openbanking.dcc.generator.type;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

class TypeMappingStrategyTest {

    private final TypeMappingStrategy postgres =
            new PostgreSqlTypeMappingStrategy(new TypeMappingProperties());

    @Test
    void defaultsReproducePreviousBehaviour() {
        assertThat(postgres.dialect()).isEqualTo("postgresql");
        assertThat(postgres.sqlType("VARCHAR", 32, null)).isEqualTo("VARCHAR(32)");
        assertThat(postgres.sqlType("NUMERIC", 18, 2)).isEqualTo("NUMERIC(18, 2)");
        assertThat(postgres.sqlType("VARCHAR", null, null)).isEqualTo("VARCHAR");
        assertThat(postgres.sqlType(null, null, null)).isEqualTo("VARCHAR");
        assertThat(postgres.javaType("BIGINT")).isEqualTo("Long");
        assertThat(postgres.javaType("DATETIME")).isEqualTo("java.time.Instant");
        assertThat(postgres.javaType("SYSTEM_UNKNOWN")).isEqualTo("String");
        assertThat(postgres.javaType(null)).isEqualTo("String");
        assertThat(postgres.openApiType("DECIMAL")).isEqualTo("number");
        assertThat(postgres.openApiType("INT")).isEqualTo("integer");
        assertThat(postgres.openApiType(null)).isEqualTo("string");
        assertThat(postgres.openApiType("WEIRD")).isEqualTo("string");
    }

    @Test
    void configOverridesDefaultsPerDialectAndJavaMapping() {
        TypeMappingProperties properties = new TypeMappingProperties();

        Map<String, Map<String, String>> dialects = new LinkedHashMap<>();
        dialects.put("postgresql", Map.of("DATETIME", "TIMESTAMP"));
        properties.setDialects(dialects);

        Map<String, String> java = new LinkedHashMap<>();
        java.put("MONEY", "java.math.BigDecimal");
        properties.setJava(java);

        TypeMappingStrategy strategy = new PostgreSqlTypeMappingStrategy(properties);

        assertThat(strategy.sqlType("DATETIME", null, null)).isEqualTo("TIMESTAMP");
        assertThat(strategy.sqlType("DATE", null, null)).isEqualTo("DATE");
        assertThat(strategy.javaType("MONEY")).isEqualTo("java.math.BigDecimal");
        assertThat(strategy.javaType("BIGINT")).isEqualTo("Long");
    }

    @Test
    void mysqlStrategyUsesItsOwnOverride() {
        TypeMappingProperties properties = new TypeMappingProperties();
        properties.setDialects(Map.of("mysql", Map.of("VARCHAR", "CHAR")));

        TypeMappingStrategy mysql = new MySqlTypeMappingStrategy(properties);

        assertThat(mysql.dialect()).isEqualTo("mysql");
        assertThat(mysql.sqlType("VARCHAR", 10, null)).isEqualTo("CHAR(10)");
    }

    @Test
    void dialectStrategyIsSelectedByProperty() {
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withUserConfiguration(StrategyConfig.class);

        runner.run(context -> assertThat(context.getBean(TypeMappingStrategy.class).dialect())
                .isEqualTo("postgresql"));

        runner.withPropertyValues("dcc.database.dialect=mysql")
                .run(context -> assertThat(context.getBean(TypeMappingStrategy.class).dialect())
                        .isEqualTo("mysql"));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(TypeMappingProperties.class)
    @Import({ PostgreSqlTypeMappingStrategy.class, MySqlTypeMappingStrategy.class })
    static class StrategyConfig {
    }
}
