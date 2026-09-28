package cn.org.openbanking.dcc.mcp.tool;

import java.util.List;

import cn.org.openbanking.dcc.application.table.TableStructureService;
import cn.org.openbanking.dcc.application.table.TableStructureService.TableDiffView;
import cn.org.openbanking.dcc.application.table.TableStructureService.TableView;
import cn.org.openbanking.dcc.application.table.TableStructureService.VersionView;
import cn.org.openbanking.dcc.core.standard.StandardStatus;
import cn.org.openbanking.dcc.generator.ddl.TableDdl;
import cn.org.openbanking.dcc.security.TenantContext;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/**
 * MCP tools for reading table structures and generating DDL. Thin adapters over
 * {@code dcc-application}; the caller's tenant comes from the authenticated context.
 */
@Component
public class TableTool {

    private final TableStructureService tables;

    public TableTool(TableStructureService tables) {
        this.tables = tables;
    }

    @McpTool(name = "get-table", description = "Return a table structure (with its current content) by id.")
    public TableView getTable(@McpToolParam(description = "Table structure id") Long id) {
        return tables.get(TenantContext.get(), id);
    }

    @McpTool(name = "list-tables",
            description = "List table structures in the caller's tenant, optionally filtered by environment/application/status.")
    public List<TableView> listTables(
            @McpToolParam(required = false, description = "Environment id filter") Long environmentId,
            @McpToolParam(required = false, description = "Application id filter") Long applicationId,
            @McpToolParam(required = false, description = "Status filter") StandardStatus status) {
        return tables.search(TenantContext.get(), environmentId, applicationId, status);
    }

    @McpTool(name = "table-history", description = "Return the version history of a table structure.")
    public List<VersionView> tableHistory(@McpToolParam(description = "Table structure id") Long id) {
        return tables.history(TenantContext.get(), id);
    }

    @McpTool(name = "diff-table", description = "Return the structural diff between two versions of a table structure.")
    public TableDiffView diffTable(
            @McpToolParam(description = "Table structure id") Long id,
            @McpToolParam(description = "From version") String from,
            @McpToolParam(description = "To version") String to) {
        return tables.diff(TenantContext.get(), id, from, to);
    }

    @McpTool(name = "generate-table-ddl",
            description = "Generate PostgreSQL CREATE TABLE DDL and a Flyway migration for a table structure.")
    public TableDdl generateTableDdl(
            @McpToolParam(description = "Table structure id") Long id,
            @McpToolParam(required = false, description = "Optional schema name") String schema) {
        return tables.createDdl(TenantContext.get(), id, schema);
    }
}
