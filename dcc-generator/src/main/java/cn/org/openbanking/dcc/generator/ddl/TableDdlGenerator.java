package cn.org.openbanking.dcc.generator.ddl;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import cn.org.openbanking.dcc.core.table.content.TableColumn;
import cn.org.openbanking.dcc.core.table.content.TableContent;
import cn.org.openbanking.dcc.core.table.content.TableIndex;
import cn.org.openbanking.dcc.core.table.diff.ColumnChange;
import cn.org.openbanking.dcc.core.table.diff.TableContentDiffer;

import org.springframework.stereotype.Component;
import org.thymeleaf.context.Context;

import cn.org.openbanking.dcc.generator.template.TemplateRenderer;
import cn.org.openbanking.dcc.generator.type.TypeMappingStrategy;

/**
 * Generates PostgreSQL DDL for a table structure: CREATE TABLE (columns, primary
 * key), COMMENTs and indexes, plus a Flyway migration wrapping it, and an
 * incremental ALTER script between two versions. The SQL shape comes from the
 * {@code ddl-create.sql} / {@code ddl-alter.sql} templates; the column/index
 * fragments are resolved here (type resolution is dialect-configurable, see
 * {@code dcc.type-mapping.*}).
 *
 * <p>Phase one targets PostgreSQL; {@code dialect} is carried in the result so
 * additional dialects can be plugged in later without changing callers.
 */
@Component
public class TableDdlGenerator {

    private final TemplateRenderer engine;
    private final TypeMappingStrategy typeMapping;

    public TableDdlGenerator(TemplateRenderer engine, TypeMappingStrategy typeMapping) {
        this.engine = engine;
        this.typeMapping = typeMapping;
    }

    /** Full CREATE DDL + Flyway script for a table at a given (timestamp) version. */
    public TableDdl generateCreate(String flywayVersion, String schema, String tableName, TableContent content,
            String sourceHash) {
        String ddl = createDdl(schema, tableName, content);
        String fileName = "V" + flywayVersion + "__create_" + tableName.toLowerCase() + ".sql";
        String script = header(tableName, sourceHash) + ddl;
        return new TableDdl(typeMapping.dialect(), ddl, fileName, script);
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

        Map<String, Object> model = new HashMap<>();
        model.put("statements", statements);
        String ddl = engine.process("ddl-alter.sql", new Context(Locale.ROOT, model));
        String fileName = "V" + flywayVersion + "__alter_" + tableName.toLowerCase() + ".sql";
        String script = header(tableName, sourceHash) + ddl;
        return new TableDdl(typeMapping.dialect(), ddl, fileName, script);
    }

    private String header(String tableName, String sourceHash) {
        // The source model's content hash is embedded so CI can detect drift: the SQL
        // is derived from a specific, immutable model version (model@sha256:...).
        return "-- generated from model@" + (sourceHash == null ? "unknown" : sourceHash)
                + " (" + tableName + ")\n";
    }

    private String createDdl(String schema, String tableName, TableContent content) {
        List<TableColumn> columns = sortedColumns(content);
        String qualified = qualify(schema, tableName);

        List<Map<String, Object>> definitions = new ArrayList<>();
        for (TableColumn column : columns) {
            definitions.add(Map.of("definition", columnDefinition(column)));
        }
        List<String> primaryKey = columns.stream()
                .filter(TableColumn::primaryKey)
                .map(TableColumn::columnName)
                .toList();
        if (!primaryKey.isEmpty()) {
            definitions.add(Map.of("definition", "CONSTRAINT " + quote("pk_" + tableName) + " PRIMARY KEY ("
                    + primaryKey.stream().map(this::quote).collect(Collectors.joining(", ")) + ")"));
        }

        List<Map<String, Object>> columnComments = new ArrayList<>();
        for (TableColumn column : columns) {
            if (column.comment() != null && !column.comment().isBlank()) {
                columnComments.add(Map.of(
                        "column", qualified + "." + quote(column.columnName()),
                        "comment", literal(column.comment())));
            }
        }

        List<String> indexes = content.indexes().stream()
                .map(index -> createIndex(schema, tableName, index))
                .toList();

        Map<String, Object> model = new HashMap<>();
        model.put("qualified", qualified);
        model.put("definitions", definitions);
        model.put("tableComment", tableComment(content));
        model.put("columnComments", columnComments);
        model.put("indexes", indexes);
        return engine.process("ddl-create.sql", new Context(Locale.ROOT, model));
    }

    private String tableComment(TableContent content) {
        String text = content.description() != null && !content.description().isBlank()
                ? content.description() : content.name();
        return text != null && !text.isBlank() ? literal(text) : null;
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
        return typeMapping.sqlType(column.dataType(), column.length(), column.scale());
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
