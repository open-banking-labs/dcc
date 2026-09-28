# dcc-starter

`dcc-starter` is the executable Spring Boot application and the web exposure layer. It calls the business logic in `dcc-api` and owns the main class, HTTP endpoints/DTOs, OpenAPI, profile configuration, Actuator and Flyway startup integration. Keep business logic out of this module; add new exposure layers (web today, RPC/MCP later) that delegate to `dcc-api`.
