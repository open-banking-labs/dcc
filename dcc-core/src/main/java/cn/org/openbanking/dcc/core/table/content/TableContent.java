package cn.org.openbanking.dcc.core.table.content;

import java.util.List;

/**
 * The versioned content of a table structure: display name, description, optional
 * shard key, and the ordered columns and indexes. Serialised as a JSON snapshot on
 * the version record.
 */
public record TableContent(
        String name,
        String description,
        String shardKey,
        List<TableColumn> columns,
        List<TableIndex> indexes) {

    public TableContent {
        columns = columns == null ? List.of() : List.copyOf(columns);
        indexes = indexes == null ? List.of() : List.copyOf(indexes);
    }
}
