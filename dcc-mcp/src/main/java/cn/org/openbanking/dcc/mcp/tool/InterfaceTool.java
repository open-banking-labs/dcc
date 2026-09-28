package cn.org.openbanking.dcc.mcp.tool;

import java.util.List;

import cn.org.openbanking.dcc.application.interfaceapi.InterfaceService;
import cn.org.openbanking.dcc.application.interfaceapi.InterfaceService.InterfaceDiffView;
import cn.org.openbanking.dcc.application.interfaceapi.InterfaceService.InterfaceView;
import cn.org.openbanking.dcc.application.interfaceapi.InterfaceService.VersionView;
import cn.org.openbanking.dcc.core.standard.StandardStatus;
import cn.org.openbanking.dcc.security.TenantContext;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/** MCP tools for reading business interfaces. Thin adapters over {@code dcc-application}. */
@Component
public class InterfaceTool {

    private final InterfaceService interfaces;

    public InterfaceTool(InterfaceService interfaces) {
        this.interfaces = interfaces;
    }

    @McpTool(name = "get-interface", description = "Return a business interface (with its current content) by id.")
    public InterfaceView getInterface(@McpToolParam(description = "Interface id") Long id) {
        return interfaces.get(TenantContext.get(), id);
    }

    @McpTool(name = "list-interfaces",
            description = "List business interfaces in the caller's tenant, optionally filtered by environment/application/status.")
    public List<InterfaceView> listInterfaces(
            @McpToolParam(required = false, description = "Environment id filter") Long environmentId,
            @McpToolParam(required = false, description = "Application id filter") Long applicationId,
            @McpToolParam(required = false, description = "Status filter") StandardStatus status) {
        return interfaces.search(TenantContext.get(), environmentId, applicationId, status);
    }

    @McpTool(name = "interface-history", description = "Return the version history of a business interface.")
    public List<VersionView> interfaceHistory(@McpToolParam(description = "Interface id") Long id) {
        return interfaces.history(TenantContext.get(), id);
    }

    @McpTool(name = "diff-interface", description = "Return the field-level diff between two versions of an interface.")
    public InterfaceDiffView diffInterface(
            @McpToolParam(description = "Interface id") Long id,
            @McpToolParam(description = "From version") String from,
            @McpToolParam(description = "To version") String to) {
        return interfaces.diff(TenantContext.get(), id, from, to);
    }
}
