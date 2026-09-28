package cn.org.openbanking.dcc.mcp.tool;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.stereotype.Component;

/**
 * MCP health tool for the DCC server. Domain tools live in {@link StandardTool}
 * and {@link ScopeTool}; every tool is a thin adapter over the protocol-agnostic
 * use cases in {@code dcc-application} and holds no business logic (see
 * ARCHITECTURE.md).
 */
@Component
public class ContractTool {

    @McpTool(name = "ping", description = "Health check for the DCC MCP server; returns 'pong'.")
    public String ping() {
        return "pong";
    }
}
