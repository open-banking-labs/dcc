# dcc-web

`dcc-web` is the executable Spring Boot application for the REST exposure layer. It calls the business logic in `dcc-application` and owns the main class, HTTP endpoints/DTOs, OpenAPI, profile configuration, Actuator and Flyway startup integration. It serves the separate front-end web UI.

Keep business logic out of this module; the MCP exposure layer (`dcc-mcp`) sits beside it and delegates to `dcc-application`.

## Entry and security

`dcc-web` publishes no host port: it is reachable only through the edge gateway, which routes `/api/**` here. Cross-cutting security (JWT verification, tenant context, method authorization, operation rate limiting) comes from `dcc-security`, and shared startup wiring (Flyway, warm-up) from `dcc-bootstrap`.
