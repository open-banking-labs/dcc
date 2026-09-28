package cn.org.openbanking.dcc.core.standard;

/**
 * The versioned content of a data standard. A {@code DataStandardVersion} stores
 * a full snapshot of these attributes so history, diff and rollback operate on
 * immutable, self-contained records.
 *
 * <p>{@code category} deliberately lives on the master {@link DataStandard} as
 * classification metadata, not on the versioned content.
 */
public record StandardContent(
        String name,
        String description,
        String dataType,
        Integer length,
        Integer scale,
        boolean required,
        String defaultValue,
        String enumValues,
        String regex,
        String exampleValue) {
}
