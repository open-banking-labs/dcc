# dcc-mcp

`dcc-mcp` is a second executable Spring Boot application that exposes the
protocol-agnostic business logic in `dcc-api` over **MCP** (Model Context
Protocol) using the **Streamable HTTP** transport. It never holds business
logic: its tools call `dcc-api` services, exactly as `dcc-starter` does over
REST.

## What it does

- Standalone app (`cn.org.openbanking.dcc.mcp.DccMcpApplication`), default port `8081`, MCP endpoint `/mcp`.
- Scans the shared `cn.org.openbanking.dcc` base package so beans from `dcc-api` and the JPA entities/repositories from `dcc-core` are picked up.
- Connects to the same PostgreSQL database as `dcc-starter` but **does not run Flyway** - it only validates (`spring.jpa.hibernate.ddl-auto: validate`). The schema is owned by `dcc-starter` / the deployment.
- Uses Spring AI's MCP server (`spring-ai-starter-mcp-server-webmvc`); the version is managed by `spring-ai-bom` (`2.0.0`).

## Adding a tool

Annotate a method on a Spring bean with `@McpTool` (from
`org.springframework.ai.mcp.annotation`). The MCP server's annotation scanner
registers it automatically.

```java
@Component
class ContractTool {

    @McpTool(name = "get-table", description = "Return a table structure by name.")
    String getTable(@McpToolParam(description = "Fully-qualified table name") String name) {
        return contractService.findTable(name); // bean from dcc-api
    }
}
```

Keep tools thin: they translate an MCP call into a `dcc-api` service call. No
business logic here.

## Configuration

Server settings live under `spring.ai.mcp.server.*` in `application.yml`:

| Property | Value | Meaning |
| --- | --- | --- |
| `spring.ai.mcp.server.name` | `dcc-mcp` | Server name reported to clients |
| `spring.ai.mcp.server.protocol` | `streamable` | Streamable HTTP transport |
| `spring.ai.mcp.server.streamable-http.mcp-endpoint` | `/mcp` | Endpoint clients connect to |
| `spring.ai.mcp.server.annotation-scanner.enabled` | `true` | Auto-register `@McpTool`/`@McpResource`/`@McpPrompt` beans |

Datasource and Redis coordinates come from the profile files
(`application-dev.yml`, `application-prod.yml`), mirroring `dcc-starter`.
