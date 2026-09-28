package cn.org.openbanking.dcc.mcp.tool;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/**
 * MCP tool adapter - a thin layer over the protocol-agnostic business logic in
 * {@code dcc-application}. Tools call {@code dcc-application} services and must not hold business
 * logic themselves (see ARCHITECTURE.md).
 *
 * <p>Placeholder: {@code dcc-application} has no business code yet, so this exposes a
 * minimal tool set that proves the MCP wiring end to end. Replace it with real
 * tools that delegate to {@code dcc-application}.
 *
 * <p>Methods annotated with {@link McpTool} on a Spring bean are registered by
 * the MCP server's annotation scanner
 * ({@code spring.ai.mcp.server.annotation-scanner.enabled}, on by default).
 */
@Component
public class ContractTool {

    @McpTool(name = "ping", description = "Health check for the DCC MCP server; returns 'pong'.")
    public String ping() {
        return "pong";
    }

    @McpTool(name = "echo", description = "Placeholder tool that echoes its input; demonstrates @McpToolParam.")
    public String echo(@McpToolParam(description = "The text to echo back") String message) {
        return message;
    }

    // Intended shape once dcc-application exposes a service, e.g.:
    //
    // @McpTool(name = "get-table", description = "Return a table structure by name.")
    // public String getTable(@McpToolParam(description = "Fully-qualified table name") String name) {
    //     return contractService.findTable(name); // bean from dcc-application
    // }

}
