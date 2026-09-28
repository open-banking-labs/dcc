package cn.org.openbanking.dcc.mcp.tool;

import java.util.List;

import cn.org.openbanking.dcc.application.standard.DataStandardService;
import cn.org.openbanking.dcc.application.standard.DataStandardService.DiffView;
import cn.org.openbanking.dcc.application.standard.DataStandardService.StandardView;
import cn.org.openbanking.dcc.application.standard.DataStandardService.VersionView;
import cn.org.openbanking.dcc.application.standard.StandardExportItem;
import cn.org.openbanking.dcc.core.standard.StandardStatus;
import cn.org.openbanking.dcc.security.TenantContext;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/**
 * MCP tools for reading and validating data standards. The caller's tenant comes
 * from the authenticated context ({@code dcc-security}), exactly as in the REST
 * layer; no business logic lives here.
 */
@Component
public class StandardTool {

    private final DataStandardService standards;

    public StandardTool(DataStandardService standards) {
        this.standards = standards;
    }

    @McpTool(name = "get-standard", description = "Return a data standard (with its current content) by id.")
    public StandardView getStandard(@McpToolParam(description = "Data standard id") Long id) {
        return standards.get(TenantContext.get(), id);
    }

    @McpTool(name = "list-standards",
            description = "List data standards in the caller's tenant, optionally filtered by environment/application/category/status.")
    public List<StandardView> listStandards(
            @McpToolParam(required = false, description = "Environment id filter") Long environmentId,
            @McpToolParam(required = false, description = "Application id filter") Long applicationId,
            @McpToolParam(required = false, description = "Category filter") String category,
            @McpToolParam(required = false, description = "Status filter: DRAFT/PENDING_REVIEW/PUBLISHED/DEPRECATED") StandardStatus status) {
        return standards.search(TenantContext.get(), environmentId, applicationId, category, status);
    }

    @McpTool(name = "standard-history", description = "Return the version history of a data standard.")
    public List<VersionView> standardHistory(@McpToolParam(description = "Data standard id") Long id) {
        return standards.history(TenantContext.get(), id);
    }

    @McpTool(name = "diff-standard", description = "Return the field-level diff between two versions of a data standard.")
    public DiffView diffStandard(
            @McpToolParam(description = "Data standard id") Long id,
            @McpToolParam(description = "From version, e.g. 1.0.0") String from,
            @McpToolParam(description = "To version, e.g. 1.1.0") String to) {
        return standards.diff(TenantContext.get(), id, from, to);
    }

    @McpTool(name = "export-standards",
            description = "Export the data standards of an environment/application in the caller's tenant.")
    public List<StandardExportItem> exportStandards(
            @McpToolParam(description = "Environment id") Long environmentId,
            @McpToolParam(description = "Application id") Long applicationId) {
        return standards.export(TenantContext.get(), environmentId, applicationId);
    }

    @McpTool(name = "find-standard-references",
            description = "List the artifacts (tables/interfaces/templates) that reference a data standard.")
    public List<String> findStandardReferences(@McpToolParam(description = "Data standard id") Long id) {
        return standards.references(TenantContext.get(), id);
    }
}
