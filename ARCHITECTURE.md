# Architecture

## Overview

DCC is a Java 17 / Spring Boot 4.1.1 Maven multi-module project for managing data-model metadata and generating downstream code and Flyway SQL.

## Modules

- `dcc-core`: foundation and persistence capabilities; JPA and PostgreSQL integration. No transport knowledge.
- `dcc-api`: the main business logic. Protocol-agnostic, so it can be exposed over HTTP, RPC or MCP without change.
- `dcc-starter`: the executable Spring Boot application and the web exposure layer - it calls `dcc-api` and adds HTTP/OpenAPI, profile configuration, Actuator and Flyway integration.

The dependencies are wired as `dcc-starter -> dcc-api -> dcc-core`. Add a new exposure protocol (RPC, MCP, ...) as its own layer on top of the unchanged `dcc-api`.

## Runtime Flow

1. `dcc-starter` loads the active Spring profile and infrastructure configuration.
2. Flyway validates and applies migrations before application startup.
3. The web layer (`dcc-starter`) handles requests and delegates to the business logic in `dcc-api`, which uses `dcc-core`.
4. PostgreSQL stores metadata; Redis and RabbitMQ are infrastructure integrations defined by Docker Compose and environment configuration.

## Architectural Rules

- Keep persistence and shared primitives in `dcc-core`.
- Keep business logic in `dcc-api` and free of transport concerns (HTTP, RPC, DTOs), so it is reusable across exposure layers.
- Keep transport concerns (HTTP endpoints, DTOs, OpenAPI) plus bootstrapping, environment wiring and operational endpoints in `dcc-starter`.
- Treat Flyway history as immutable; corrections are new forward migrations.
- Keep secrets and environment-specific values outside source control.
