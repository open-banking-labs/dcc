package cn.org.openbanking.dcc.core.table.diff;

import java.util.List;

import cn.org.openbanking.dcc.core.standard.diff.FieldChange;

/** A changed column: how it changed and the attribute-level changes that drove it. */
public record ColumnChange(String columnName, ChangeKind kind, List<FieldChange> fields) {

    public ColumnChange {
        fields = fields == null ? List.of() : List.copyOf(fields);
    }
}
