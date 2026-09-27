# Architecture

## Overview

DCC is a Java 17 / Spring Boot 4.1.1 Maven multi-module project for managing data-model metadata and generating downstream code and Flyway SQL.

## Modules

- `dcc-core`: domain and persistence capabilities; JPA and PostgreSQL integration.
- `dcc-api`: HTTP API surface, MVC endpoints, DTOs and OpenAPI support.
- `dcc-starter`: executable Spring Boot application, runtime configuration, Actuator and Flyway integration.

The modules currently build independently. The intended dependency direction is `dcc-starter -> dcc-api -> dcc-core`; add Maven dependencies only when code is actually shared across those boundaries.

## Runtime Flow

1. `dcc-starter` loads the active Spring profile and infrastructure configuration.
2. Flyway validates and applies migrations before application startup.
3. API requests are handled by the web layer and delegated to domain/persistence services.
4. PostgreSQL stores metadata; Redis and RabbitMQ are infrastructure integrations defined by Docker Compose and environment configuration.

## Architectural Rules

- Keep domain and persistence concerns in `dcc-core`.
- Keep transport concerns (HTTP, DTOs, OpenAPI) in `dcc-api`.
- Keep bootstrapping, environment wiring and operational endpoints in `dcc-starter`.
- Treat Flyway history as immutable; corrections are new forward migrations.
- Keep secrets and environment-specific values outside source control.
