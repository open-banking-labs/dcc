package cn.org.openbanking.dcc.generator.type;

import java.util.Map;

/**
 * Base {@link TypeMappingStrategy} that reads the SQL mapping for its dialect from
 * {@link TypeMappingProperties} and applies the built-in defaults for anything not
 * overridden. The defaults reproduce the pre-configuration hard-coded mappings, so a
 * deployment that sets no type-mapping config is unaffected.
 */
public abstract class AbstractTypeMappingStrategy implements TypeMappingStrategy {

    /** Logical type -&gt; Java type. Mirrors the historical {@code JavaTypes} switch. */
    private static final Map<String, String> DEFAULT_JAVA = Map.ofEntries(
            Map.entry("INT", "Integer"),
            Map.entry("INTEGER", "Integer"),
            Map.entry("SMALLINT", "Integer"),
            Map.entry("BIGINT", "Long"),
            Map.entry("LONG", "Long"),
            Map.entry("DECIMAL", "java.math.BigDecimal"),
            Map.entry("NUMERIC", "java.math.BigDecimal"),
            Map.entry("NUMBER", "java.math.BigDecimal"),
            Map.entry("BOOLEAN", "Boolean"),
            Map.entry("BOOL", "Boolean"),
            Map.entry("DATE", "java.time.LocalDate"),
            Map.entry("TIMESTAMP", "java.time.Instant"),
            Map.entry("DATETIME", "java.time.Instant"),
            Map.entry("INSTANT", "java.time.Instant"));

    /** Logical type -&gt; OpenAPI/JSON type. Mirrors the historical {@code OpenApiGenerator} switch. */
    private static final Map<String, String> DEFAULT_OPENAPI = Map.ofEntries(
            Map.entry("INT", "integer"),
            Map.entry("INTEGER", "integer"),
            Map.entry("SMALLINT", "integer"),
            Map.entry("BIGINT", "integer"),
            Map.entry("LONG", "integer"),
            Map.entry("DECIMAL", "number"),
            Map.entry("NUMERIC", "number"),
            Map.entry("NUMBER", "number"),
            Map.entry("BOOLEAN", "boolean"),
            Map.entry("BOOL", "boolean"));

    private final TypeMappingProperties properties;

    protected AbstractTypeMappingStrategy(TypeMappingProperties properties) {
        this.properties = properties;
    }

    @Override
    public String sqlType(String dataType, Integer length, Integer scale) {
        String base = sqlBaseType(dataType);
        if (base.contains("(") || length == null) {
            return base;
        }
        return scale == null
                ? base + "(" + length + ")"
                : base + "(" + length + ", " + scale + ")";
    }

    @Override
    public String javaType(String dataType) {
        if (dataType == null || dataType.isBlank()) {
            return "String";
        }
        String key = dataType.trim().toUpperCase();
        return lookup(properties.getJava(), key, DEFAULT_JAVA, "String");
    }

    @Override
    public String openApiType(String dataType) {
        if (dataType == null) {
            return "string";
        }
        String key = dataType.trim().toUpperCase();
        return lookup(properties.getOpenapi(), key, DEFAULT_OPENAPI, "string");
    }

    private String sqlBaseType(String dataType) {
        if (dataType == null) {
            return "VARCHAR";
        }
        String key = dataType.toUpperCase();
        Map<String, String> dialectMapping = properties.getDialects().getOrDefault(dialect(), Map.of());
        String mapped = dialectMapping.get(key);
        return mapped != null ? mapped : key;
    }

    private static String lookup(Map<String, String> overrides, String key, Map<String, String> defaults,
            String fallback) {
        String override = overrides == null ? null : overrides.get(key);
        if (override != null) {
            return override;
        }
        return defaults.getOrDefault(key, fallback);
    }
}
