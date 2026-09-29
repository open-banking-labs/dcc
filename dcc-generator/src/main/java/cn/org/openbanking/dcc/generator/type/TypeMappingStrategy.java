package cn.org.openbanking.dcc.generator.type;

/**
 * Maps a data standard's logical type to the physical type of a given target
 * database dialect (SQL), to Java, and to OpenAPI/JSON. Implementations are selected
 * by {@code dcc.database.dialect} (see the concrete strategies) so that adding a
 * dialect means adding one strategy class plus, optionally, its mapping config —
 * never a {@code switch} over dialects.
 */
public interface TypeMappingStrategy {

    /** The dialect this strategy maps to, e.g. {@code postgresql}. */
    String dialect();

    /** Physical SQL type for a logical type, including {@code (length[, scale])}. */
    String sqlType(String dataType, Integer length, Integer scale);

    /** Java type for a logical type. */
    String javaType(String dataType);

    /** OpenAPI/JSON type ({@code string|integer|number|boolean}) for a logical type. */
    String openApiType(String dataType);
}
