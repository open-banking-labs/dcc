package cn.org.openbanking.dcc.core.table.diff;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import cn.org.openbanking.dcc.core.standard.diff.FieldChange;
import cn.org.openbanking.dcc.core.table.content.TableColumn;
import cn.org.openbanking.dcc.core.table.content.TableContent;
import cn.org.openbanking.dcc.core.table.content.TableIndex;

/**
 * Structural comparison of two {@link TableContent} snapshots. Columns are matched
 * by column name; indexes by index name. The result drives both the diff API and
 * the semantic-version bump.
 */
public final class TableContentDiffer {

    private TableContentDiffer() {
    }

    public static TableChangeSet between(TableContent before, TableContent after) {
        List<ColumnChange> columns = diffColumns(before.columns(), after.columns());

        List<FieldChange> tableFields = new ArrayList<>();
        add(tableFields, "table.name", before.name(), after.name());
        add(tableFields, "table.description", before.description(), after.description());
        add(tableFields, "table.shardKey", before.shardKey(), after.shardKey());

        return new TableChangeSet(columns, tableFields, diffIndexes(before.indexes(), after.indexes()));
    }

    private static List<ColumnChange> diffColumns(List<TableColumn> before, List<TableColumn> after) {
        Map<String, TableColumn> oldByName = byName(before, TableColumn::columnName);
        Map<String, TableColumn> newByName = byName(after, TableColumn::columnName);
        List<ColumnChange> changes = new ArrayList<>();

        for (TableColumn column : after) {
            TableColumn previous = oldByName.get(column.columnName());
            if (previous == null) {
                changes.add(new ColumnChange(column.columnName(), ChangeKind.ADDED, List.of()));
            } else {
                List<FieldChange> fields = diffColumn(previous, column);
                if (!fields.isEmpty()) {
                    changes.add(new ColumnChange(column.columnName(), ChangeKind.MODIFIED, fields));
                }
            }
        }
        for (TableColumn column : before) {
            if (!newByName.containsKey(column.columnName())) {
                changes.add(new ColumnChange(column.columnName(), ChangeKind.REMOVED, List.of()));
            }
        }
        return changes;
    }

    private static List<FieldChange> diffColumn(TableColumn before, TableColumn after) {
        List<FieldChange> fields = new ArrayList<>();
        add(fields, "dataType", before.dataType(), after.dataType());
        add(fields, "length", before.length(), after.length());
        add(fields, "scale", before.scale(), after.scale());
        add(fields, "nullable", before.nullable(), after.nullable());
        add(fields, "primaryKey", before.primaryKey(), after.primaryKey());
        add(fields, "defaultValue", before.defaultValue(), after.defaultValue());
        add(fields, "comment", before.comment(), after.comment());
        if (before.sortOrder() != after.sortOrder()) {
            add(fields, "sortOrder", before.sortOrder(), after.sortOrder());
        }
        return fields;
    }

    private static List<FieldChange> diffIndexes(List<TableIndex> before, List<TableIndex> after) {
        Map<String, TableIndex> oldByName = byName(before, TableIndex::name);
        Map<String, TableIndex> newByName = byName(after, TableIndex::name);
        List<FieldChange> fields = new ArrayList<>();
        for (TableIndex index : after) {
            TableIndex previous = oldByName.get(index.name());
            if (previous == null) {
                fields.add(new FieldChange("index." + index.name(), null, render(index)));
            } else if (!previous.equals(index)) {
                fields.add(new FieldChange("index." + index.name(), render(previous), render(index)));
            }
        }
        for (TableIndex index : before) {
            if (!newByName.containsKey(index.name())) {
                fields.add(new FieldChange("index." + index.name(), render(index), null));
            }
        }
        return fields;
    }

    private static <T> Map<String, T> byName(List<T> items, java.util.function.Function<T, String> key) {
        Map<String, T> map = new LinkedHashMap<>();
        for (T item : items) {
            map.put(key.apply(item), item);
        }
        return map;
    }

    private static void add(List<FieldChange> changes, String field, Object before, Object after) {
        if (!Objects.equals(before, after)) {
            changes.add(new FieldChange(field, render(before), render(after)));
        }
    }

    private static String render(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
