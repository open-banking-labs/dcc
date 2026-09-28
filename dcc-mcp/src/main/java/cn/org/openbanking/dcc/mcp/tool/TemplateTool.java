package cn.org.openbanking.dcc.mcp.tool;

import java.util.List;

import cn.org.openbanking.dcc.application.template.TemplateService;
import cn.org.openbanking.dcc.application.template.TemplateService.TemplateDiffView;
import cn.org.openbanking.dcc.application.template.TemplateService.TemplateView;
import cn.org.openbanking.dcc.application.template.TemplateService.VersionView;
import cn.org.openbanking.dcc.core.standard.StandardStatus;
import cn.org.openbanking.dcc.security.TenantContext;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/** MCP tools for reading interface templates. Thin adapters over {@code dcc-application}. */
@Component
public class TemplateTool {

    private final TemplateService templates;

    public TemplateTool(TemplateService templates) {
        this.templates = templates;
    }

    @McpTool(name = "get-template", description = "Return an interface template (with its Head/Body/Trailer content) by id.")
    public TemplateView getTemplate(@McpToolParam(description = "Template id") Long id) {
        return templates.get(TenantContext.get(), id);
    }

    @McpTool(name = "list-templates",
            description = "List interface templates in the caller's tenant, optionally filtered by environment/application/status.")
    public List<TemplateView> listTemplates(
            @McpToolParam(required = false, description = "Environment id filter") Long environmentId,
            @McpToolParam(required = false, description = "Application id filter") Long applicationId,
            @McpToolParam(required = false, description = "Status filter") StandardStatus status) {
        return templates.search(TenantContext.get(), environmentId, applicationId, status);
    }

    @McpTool(name = "template-history", description = "Return the version history of an interface template.")
    public List<VersionView> templateHistory(@McpToolParam(description = "Template id") Long id) {
        return templates.history(TenantContext.get(), id);
    }

    @McpTool(name = "diff-template", description = "Return the field-level diff between two versions of a template.")
    public TemplateDiffView diffTemplate(
            @McpToolParam(description = "Template id") Long id,
            @McpToolParam(description = "From version") String from,
            @McpToolParam(description = "To version") String to) {
        return templates.diff(TenantContext.get(), id, from, to);
    }
}
