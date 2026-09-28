package cn.org.openbanking.dcc.core.table.content;

import java.util.List;

/** One index of a table structure (name, uniqueness and the ordered column names). */
public record TableIndex(String name, boolean unique, List<String> columns) {

    public TableIndex {
        columns = columns == null ? List.of() : List.copyOf(columns);
    }
}
