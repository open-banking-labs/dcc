package cn.org.openbanking.dcc.core.table.diff;

import java.util.List;

import cn.org.openbanking.dcc.core.standard.diff.FieldChange;

/**
 * Structural diff between two table structure versions: column-level changes plus
 * table-level (name/description/shardKey) and index changes.
 */
public record TableChangeSet(
        List<ColumnChange> columns,
        List<FieldChange> tableFields,
        List<FieldChange> indexFields) {

    public TableChangeSet {
        columns = columns == null ? List.of() : List.copyOf(columns);
        tableFields = tableFields == null ? List.of() : List.copyOf(tableFields);
        indexFields = indexFields == null ? List.of() : List.copyOf(indexFields);
    }

    public boolean isEmpty() {
        return columns.isEmpty() && tableFields.isEmpty() && indexFields.isEmpty();
    }
}
