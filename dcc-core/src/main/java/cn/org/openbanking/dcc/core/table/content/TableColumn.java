package cn.org.openbanking.dcc.core.table.content;

/**
 * One column of a table structure. It references a {@link cn.org.openbanking.dcc.core.standard.DataStandard}
 * for the physical type and lets the table override the column name, nullability,
 * primary-key flag, default and ordering.
 *
 * <p>The resolved type attributes ({@code dataType}, {@code length}, {@code scale})
 * are copied into the snapshot when the column is defined, so a table version is a
 * self-contained, immutable document that DDL can be generated from without
 * re-reading the standard.
 */
public record TableColumn(
        String columnName,
        Long standardId,
        String standardCode,
        String dataType,
        Integer length,
        Integer scale,
        boolean nullable,
        boolean primaryKey,
        String defaultValue,
        int sortOrder,
        String comment) {
}
