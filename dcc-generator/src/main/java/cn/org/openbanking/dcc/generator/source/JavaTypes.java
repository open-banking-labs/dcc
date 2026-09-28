package cn.org.openbanking.dcc.generator.source;

/**
 * Maps a data standard's logical type to a Java type. Java is the primary target;
 * a parallel mapping per language can be added later without touching callers.
 */
public final class JavaTypes {

    private JavaTypes() {
    }

    public static String toJavaType(String dataType, Integer length, Integer scale) {
        if (dataType == null || dataType.isBlank()) {
            return "String";
        }
        return switch (dataType.trim().toUpperCase()) {
            case "INT", "INTEGER", "SMALLINT" -> "Integer";
            case "BIGINT", "LONG" -> "Long";
            case "DECIMAL", "NUMERIC", "NUMBER" -> "java.math.BigDecimal";
            case "BOOLEAN", "BOOL" -> "Boolean";
            case "DATE" -> "java.time.LocalDate";
            case "TIMESTAMP", "DATETIME", "INSTANT" -> "java.time.Instant";
            default -> "String";
        };
    }

    public static String capitalize(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }
}
