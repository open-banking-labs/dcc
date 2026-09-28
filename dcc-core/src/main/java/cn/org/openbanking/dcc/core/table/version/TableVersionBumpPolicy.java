package cn.org.openbanking.dcc.core.table.version;

import java.util.ArrayList;
import java.util.List;

import cn.org.openbanking.dcc.core.common.error.ValidationException;
import cn.org.openbanking.dcc.core.standard.diff.FieldChange;
import cn.org.openbanking.dcc.core.standard.version.VersionBump;
import cn.org.openbanking.dcc.core.standard.version.VersionChangeType;
import cn.org.openbanking.dcc.core.table.diff.ColumnChange;
import cn.org.openbanking.dcc.core.table.diff.TableChangeSet;

/**
 * Semantic-version rule for table structures:
 * <ul>
 *   <li><b>MAJOR</b> - a column removed, or a column's type/constraint changed, or
 *       the shard key changed (breaking for downstream consumers);</li>
 *   <li><b>MINOR</b> - a column added, the display name changed, or an index changed
 *       (backward compatible);</li>
 *   <li><b>PATCH</b> - description/comment/default changes.</li>
 * </ul>
 * The most severe change wins.
 */
public final class TableVersionBumpPolicy {

    private TableVersionBumpPolicy() {
    }

    public static VersionBump determine(TableChangeSet changes) {
        if (changes.isEmpty()) {
            throw new ValidationException("no content change: nothing to version");
        }
        VersionChangeType type = VersionChangeType.PATCH;
        List<String> reasons = new ArrayList<>();

        for (ColumnChange column : changes.columns()) {
            switch (column.kind()) {
                case ADDED -> {
                    type = VersionChangeType.highest(type, VersionChangeType.MINOR);
                    reasons.add("column " + column.columnName() + " added");
                }
                case REMOVED -> {
                    type = VersionChangeType.highest(type, VersionChangeType.MAJOR);
                    reasons.add("column " + column.columnName() + " removed");
                }
                case MODIFIED -> {
                    VersionChangeType columnType = classifyColumn(column.fields());
                    type = VersionChangeType.highest(type, columnType);
                    reasons.add("column " + column.columnName() + " changed (" + columnType + ")");
                }
            }
        }
        for (FieldChange field : changes.tableFields()) {
            VersionChangeType fieldType = switch (field.field()) {
                case "table.shardKey" -> VersionChangeType.MAJOR;
                case "table.name" -> VersionChangeType.MINOR;
                default -> VersionChangeType.PATCH;
            };
            type = VersionChangeType.highest(type, fieldType);
            reasons.add(field.field() + " changed");
        }
        if (!changes.indexFields().isEmpty()) {
            type = VersionChangeType.highest(type, VersionChangeType.MINOR);
            reasons.add(changes.indexFields().size() + " index change(s)");
        }
        return new VersionBump(type, reasons);
    }

    private static VersionChangeType classifyColumn(List<FieldChange> fields) {
        VersionChangeType type = VersionChangeType.PATCH;
        for (FieldChange field : fields) {
            VersionChangeType fieldType = switch (field.field()) {
                case "dataType", "length", "scale", "nullable", "primaryKey" -> VersionChangeType.MAJOR;
                default -> VersionChangeType.PATCH;
            };
            type = VersionChangeType.highest(type, fieldType);
        }
        return type;
    }
}
