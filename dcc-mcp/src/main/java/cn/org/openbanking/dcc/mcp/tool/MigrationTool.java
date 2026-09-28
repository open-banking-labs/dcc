package cn.org.openbanking.dcc.mcp.tool;

import java.util.List;

import cn.org.openbanking.dcc.application.migration.MigrationService;
import cn.org.openbanking.dcc.application.migration.MigrationService.MigrationOrderView;
import cn.org.openbanking.dcc.core.migration.MigrationStatus;
import cn.org.openbanking.dcc.security.TenantContext;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/** MCP tools for reading migration orders. Thin adapters over {@code dcc-application}. */
@Component
public class MigrationTool {

    private final MigrationService migrations;

    public MigrationTool(MigrationService migrations) {
        this.migrations = migrations;
    }

    @McpTool(name = "get-migration", description = "Return an environment migration order by id.")
    public MigrationOrderView getMigration(@McpToolParam(description = "Migration order id") Long id) {
        return migrations.get(TenantContext.get(), id);
    }

    @McpTool(name = "list-migrations",
            description = "List migration orders in the caller's tenant, optionally filtered by target environment/status.")
    public List<MigrationOrderView> listMigrations(
            @McpToolParam(required = false, description = "Target environment id filter") Long targetEnvironmentId,
            @McpToolParam(required = false, description = "Status filter") MigrationStatus status) {
        return migrations.search(TenantContext.get(), targetEnvironmentId, status);
    }
}
