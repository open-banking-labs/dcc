package cn.org.openbanking.dcc.generator.source;

import cn.org.openbanking.dcc.generator.type.PostgreSqlTypeMappingStrategy;
import cn.org.openbanking.dcc.generator.type.TypeMappingProperties;
import cn.org.openbanking.dcc.generator.type.TypeMappingStrategy;

/**
 * Small helpers for Java source generation.
 *
 * <p>The logical-type -&gt; Java-type mapping is now configuration-driven (see
 * {@code dcc.type-mapping.java.*} and {@link cn.org.openbanking.dcc.generator.type.TypeMappingStrategy}).
 * {@link #toJavaType} is kept as a delegating shim so existing callers keep working;
 * the hard-coded {@code switch} it used to contain is gone.
 */
public final class JavaTypes {

    private static final TypeMappingStrategy DEFAULT =
            new PostgreSqlTypeMappingStrategy(new TypeMappingProperties());

    private JavaTypes() {
    }

    public static String toJavaType(String dataType, Integer length, Integer scale) {
        return DEFAULT.javaType(dataType);
    }

    public static String capitalize(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }
}
