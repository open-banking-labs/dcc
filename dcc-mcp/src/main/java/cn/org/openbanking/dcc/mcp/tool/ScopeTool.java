package cn.org.openbanking.dcc.mcp.tool;

import java.util.List;

import cn.org.openbanking.dcc.application.tenant.TenantApplicationService;
import cn.org.openbanking.dcc.application.tenant.TenantApplicationService.ApplicationView;
import cn.org.openbanking.dcc.application.tenant.TenantEnvironmentService;
import cn.org.openbanking.dcc.application.tenant.TenantEnvironmentService.EnvironmentView;
import cn.org.openbanking.dcc.security.TenantContext;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.stereotype.Component;

/**
 * MCP tools for discovering the caller's environment/application scope, so an AI
 * assistant can resolve the ids it needs before querying standards.
 */
@Component
public class ScopeTool {

    private final TenantEnvironmentService environments;
    private final TenantApplicationService applications;

    public ScopeTool(TenantEnvironmentService environments, TenantApplicationService applications) {
        this.environments = environments;
        this.applications = applications;
    }

    @McpTool(name = "list-environments", description = "List the environments of the caller's tenant.")
    public List<EnvironmentView> listEnvironments() {
        return environments.list(TenantContext.get());
    }

    @McpTool(name = "list-applications", description = "List the applications of the caller's tenant.")
    public List<ApplicationView> listApplications() {
        return applications.list(TenantContext.get());
    }
}
