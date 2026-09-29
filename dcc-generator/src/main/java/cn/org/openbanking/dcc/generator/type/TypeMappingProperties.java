package cn.org.openbanking.dcc.generator.type;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Type-mapping configuration, bound from {@code dcc.type-mapping.*}. All maps default
 * to empty; the strategy layers these user-supplied values over built-in defaults, so
 * omitting everything reproduces the previous behaviour exactly.
 *
 * <pre>
 * dcc:
 *   type-mapping:
 *     java:            # logical data type -> Java type
 *       INT: Long
 *     openapi:         # logical data type -> OpenAPI/JSON type
 *       MONEY: number
 *     dialects:        # dialect -> (logical data type -> physical SQL type)
 *       postgresql:
 *         DATETIME: TIMESTAMP
 *       mysql:
 *         TIMESTAMP: DATETIME
 * </pre>
 */
@ConfigurationProperties(prefix = "dcc.type-mapping")
public class TypeMappingProperties {

    /** Logical data type (upper-cased) -&gt; Java type. */
    private Map<String, String> java = new LinkedHashMap<>();

    /** Logical data type (upper-cased) -&gt; OpenAPI/JSON type. */
    private Map<String, String> openapi = new LinkedHashMap<>();

    /** Dialect -&gt; (logical data type (upper-cased) -&gt; physical SQL type). */
    private Map<String, Map<String, String>> dialects = new LinkedHashMap<>();

    public Map<String, String> getJava() {
        return java;
    }

    public void setJava(Map<String, String> java) {
        this.java = java;
    }

    public Map<String, String> getOpenapi() {
        return openapi;
    }

    public void setOpenapi(Map<String, String> openapi) {
        this.openapi = openapi;
    }

    public Map<String, Map<String, String>> getDialects() {
        return dialects;
    }

    public void setDialects(Map<String, Map<String, String>> dialects) {
        this.dialects = dialects;
    }
}
