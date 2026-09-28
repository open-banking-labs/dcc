package cn.org.openbanking.dcc.generator.ddl;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import cn.org.openbanking.dcc.core.table.content.TableColumn;
import cn.org.openbanking.dcc.core.table.content.TableContent;
import cn.org.openbanking.dcc.core.table.content.TableIndex;
import cn.org.openbanking.dcc.core.table.diff.ChangeKind;
import cn.org.openbanking.dcc.core.table.diff.ColumnChange;
import cn.org.openbanking.dcc.core.table.diff.TableContentDiffer;

import org.springframework.stereotype.Component;

/**
 * Generates PostgreSQL DDL for a table structure: CREATE TABLE (columns, primary
 * key), COMMENTs and indexes, plus a Flyway migration wrapping it. Also produces an
 * incremental ALTER script between two versions.
 *
 * <p>Phase one targets PostgreSQL; {@code dialect} is carried in the result so
 * additional dialects can be plugged in later without changing callers.
 */
@Component
public class TableDdlGenerator {

    private static final String DIALECT = "postgresql";

    /** Full CREATE DDL + Flyway script for a table at a given (timestamp) version. */
    public TableDdl generateCreate(String flywayVersion, String schema, String tableName, TableContent content,
            String sourceHash) {
        String ddl = createTableDdl(schema, tableName, content);
        String fileName = "V" + flywayVersion + "__create_" + tableName.toLowerCase() + ".sql";
        String script = header(tableName, sourceHash) + ddl;
        return new TableDdl(DIALECT, ddl, fileName, script);
    }

    /** Incremental ALTER DDL that migrates a table from {@code before} to {@code after}. */
    public TableDdl generateAlter(String flywayVersion, String schema, String tableName,
            TableContent before, TableContent after, String sourceHash) {
        List<String> statements = new ArrayList<>();
        String qualified = qualify(schema, tableName);

        for (ColumnChange change : TableContentDiffer.between(before, after).columns()) {
            switch (change.kind()) {
                case ADDED -> statements.add("ALTER TABLE " + qualified + " ADD COLUMN "
                        + columnDefinition(column(after, change.columnName())) + ";");
                case REMOVED -> statements.add("ALTER TABLE " + qualified + " DROP COLUMN "
                        + quote(change.columnName()) + ";");
                case MODIFIED -> statements.addAll(alterColumn(qualified, column(before, change.columnName()),
                        column(after, change.columnName())));
            }
        }

        String ddl = statements.isEmpty() ? "-- no structural column change" : String.join("\n", statements) + "\n";
        String fileName = "V" + flywayVersion + "__alter_" + tableName.toLowerCase() + ".sql";
        String script = header(tableName, sourceHash) + ddl;
        return new TableDdl(DIALECT, ddl, fileName, script);
    }

    private String header(String tableName, String sourceHash) {
        // The source model's content hash is embedded so CI can detect drift: the SQL
        // is derived from a specific, immutable model version (model@sha256:...).
        return "-- generated from model@" + (sourceHash == null ? "unknown" : sourceHash)
                + " (" + tableName + ")\n";
    }

    private String createTableDdl(String schema, String tableName, TableContent content) {
        List<TableColumn> columns = sortedColumns(content);
        List<String> definitions = new ArrayList<>();
        for (TableColumn column : columns) {
            definitions.add(columnDefinition(column));
        }
        List<String> primaryKey = columns.stream()
                .filter(TableColumn::primaryKey)
                .map(TableColumn::columnName)
                .toList();
        if (!primaryKey.isEmpty()) {
            definitions.add("CONSTRAINT " + quote("pk_" + tableName) + " PRIMARY KEY ("
                    + primaryKey.stream().map(this::quote).collect(Collectors.joining(", ")) + ")");
        }

        StringBuilder sb = new StringBuilder();
        String qualified = qualify(schema, tableName);
        sb.append("CREATE TABLE ").append(qualified).append(" (\n  ")
                .append(String.join(",\n  ", definitions))
                .append("\n);\n");

        String tableComment = content.description() != null && !content.description().isBlank()
                ? content.description() : content.name();
        if (tableComment != null && !tableComment.isBlank()) {
            sb.append("COMMENT ON TABLE ").append(qualified).append(" IS ").append(literal(tableComment)).append(";\n");
        }
        for (TableColumn column : columns) {
            if (column.comment() != null && !column.comment().isBlank()) {
                sb.append("COMMENT ON COLUMN ").append(qualified).append('.').append(quote(column.columnName()))
                        .append(" IS ").append(literal(column.comment())).append(";\n");
            }
        }
        for (TableIndex index : content.indexes()) {
            sb.append(createIndex(schema, tableName, index)).append('\n');
        }
        return sb.toString();
    }

    private List<String> alterColumn(String qualified, TableColumn before, TableColumn after) {
        List<String> statements = new ArrayList<>();
        if (!typeSignature(before).equals(typeSignature(after))) {
            statements.add("ALTER TABLE " + qualified + " ALTER COLUMN " + quote(after.columnName())
                    + " TYPE " + typeOf(after) + ";");
        }
        if (before.nullable() != after.nullable()) {
            statements.add("ALTER TABLE " + qualified + " ALTER COLUMN " + quote(after.columnName())
                    + (after.nullable() ? " DROP NOT NULL;" : " SET NOT NULL;"));
        }
        return statements;
    }

    private String createIndex(String schema, String tableName, TableIndex index) {
        String uniqueness = index.unique() ? "UNIQUE " : "";
        String columns = index.columns().stream().map(this::quote).collect(Collectors.joining(", "));
        return "CREATE " + uniqueness + "INDEX " + quote(index.name()) + " ON " + qualify(schema, tableName)
                + " (" + columns + ");";
    }

    private String columnDefinition(TableColumn column) {
        StringBuilder sb = new StringBuilder(quote(column.columnName())).append(' ').append(typeOf(column));
        if (column.defaultValue() != null && !column.defaultValue().isBlank()) {
            sb.append(" DEFAULT ").append(column.defaultValue());
        }
        if (column.primaryKey() || !column.nullable()) {
            sb.append(" NOT NULL");
        }
        return sb.toString();
    }

    private String typeOf(TableColumn column) {
        String type = column.dataType() == null ? "VARCHAR" : column.dataType().toUpperCase();
        if (type.contains("(") || column.length() == null) {
            return type;
        }
        return column.scale() == null
                ? type + "(" + column.length() + ")"
                : type + "(" + column.length() + ", " + column.scale() + ")";
    }

    private String typeSignature(TableColumn column) {
        return typeOf(column).toUpperCase() + "|" + column.nullable();
    }

    private TableColumn column(TableContent content, String columnName) {
        return content.columns().stream()
                .filter(c -> c.columnName().equals(columnName))
                .findFirst()
                .orElseThrow();
    }

    private List<TableColumn> sortedColumns(TableContent content) {
        return content.columns().stream().sorted(Comparator.comparingInt(TableColumn::sortOrder)).toList();
    }

    private String qualify(String schema, String table) {
        return schema == null || schema.isBlank() ? quote(table) : quote(schema) + "." + quote(table);
    }

    private String quote(String identifier) {
        return "\"" + identifier + "\"";
    }

    private String literal(String value) {
        return "'" + value.replace("'", "''") + "'";
    }
}
