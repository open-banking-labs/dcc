# ADR 0001: Use PostgreSQL for Metadata Storage

- Status: Accepted
- Date: 2026-09-27

## Context

DCC stores structured metadata such as field definitions, table structures, indexes and shard keys. The application already integrates Spring Data JPA and requires transactional schema migrations.

## Decision

Use PostgreSQL as the primary metadata database, managed through Spring Data JPA and Flyway.

## Consequences

- Schema changes are versioned and deployed as Flyway migrations.
- PostgreSQL-specific behavior must be documented when used.
- Local development requires PostgreSQL, normally supplied through Docker Compose.
