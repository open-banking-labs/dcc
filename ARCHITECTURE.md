# Architecture

## Overview

DCC is a Java 17 / Spring Boot 4.1.1 Maven multi-module project for managing data-model metadata and generating downstream code and Flyway SQL.

## Modules

- `dcc-core`: foundation and persistence capabilities; JPA and PostgreSQL integration. No transport knowledge.
- `dcc-application`: the main business logic (use-case layer). Protocol-agnostic, so it can be exposed over HTTP, RPC or MCP without change.
- `dcc-bootstrap`: shared runtime/bootstrap wiring reused by the runnable apps - Flyway startup integration, the warm-up framework and common infrastructure conventions. No transport or business logic.
- `dcc-security`: shared, in-process security for the exposure layers - verifies the caller's JWT, establishes the tenant context, enables method-level authorization and provides operation-level rate limiting.
- `dcc-web`: the executable Spring Boot application for the REST exposure layer. It calls `dcc-application` and adds HTTP/OpenAPI, profile configuration, Actuator and Flyway integration. It serves the separate front-end web UI.
- `dcc-mcp`: a second executable Spring Boot application that exposes the same `dcc-application` business logic over MCP (Streamable HTTP).
- The edge gateway (a Traefik service in `docker-compose.yml`) is the single public entry: it routes `/api` to `dcc-web` and `/mcp` to `dcc-mcp`, applies a coarse rate limit, and leaves token verification to the exposure layers.

Dependencies: `dcc-application -> dcc-core`; `dcc-web` and `dcc-mcp` each depend on `dcc-application`, `dcc-security` and `dcc-bootstrap`. Add a new exposure protocol as its own thin layer on top of the unchanged `dcc-application`.

## Runtime Flow

1. The edge gateway receives every client request (TLS, routing, coarse rate limit).
2. `dcc-web` loads the active Spring profile and infrastructure configuration.
3. Flyway validates and applies migrations before application startup (owned by `dcc-web`; `dcc-mcp` never migrates).
4. `dcc-security` verifies the caller's bearer JWT and sets the tenant context (both apps).
5. The REST layer (`dcc-web`) handles requests and delegates to the business logic in `dcc-application`, which uses `dcc-core`.
6. The MCP layer (`dcc-mcp`) handles MCP tool calls and delegates to the same business logic in `dcc-application`; it connects to the same database but does not migrate it.
7. PostgreSQL stores metadata; Redis and RabbitMQ are infrastructure integrations defined by Docker Compose and environment configuration.

## Architectural Rules

- Keep persistence and shared primitives in `dcc-core`.
- Keep business logic in `dcc-application` and free of transport concerns (HTTP, RPC, DTOs), so it is reusable across exposure layers.
- Keep transport concerns (HTTP endpoints, DTOs, OpenAPI, or MCP tools) plus bootstrapping and operational endpoints in the exposure layers (`dcc-web`, `dcc-mcp`).
- Keep cross-cutting security (authentication, tenant context, authorization, operation rate limiting) in `dcc-security` - one implementation shared by every exposure layer.
- Keep shared startup wiring (Flyway, warm-up, infrastructure conventions) in `dcc-bootstrap`.
- The exposure layers publish no host ports and are reachable only through the edge gateway; authorization decisions are made inside `dcc-security`, not duplicated per app.
- Treat Flyway history as immutable; corrections are new forward migrations.
- Keep secrets and environment-specific values outside source control.
