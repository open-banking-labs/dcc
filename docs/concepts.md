# Concepts

DCC manages **metadata about data** — never the data itself. Everything is scoped to a
tenant and versioned.

## Field Standard Library (数标)

The **data standard** (`DataStandard`) is the reusable unit: one field definition with
a logical `dataType`, `length`, `scale`, `required`, optional `regex`, `defaultValue`
and `enumValues`, plus documentation. Artifacts bind standards rather than re-declaring
field types, so a type change is made in one place.

A standard's physical SQL type and Java/OpenAPI type are resolved at generation time by
the dialect-aware type mapping (`dcc.type-mapping.*` / `dcc.database.dialect`), see
[configuration.md](configuration.md).

## Artifacts

| Artifact | What it models | Content |
| --- | --- | --- |
| Data standard | a reusable field | type, constraints, docs |
| Table structure | a physical table | columns (each bound to a standard), indexes, primary key, shard key |
| Interface (业务接口) | a request/response contract | input/output fields, URL, method |
| Interface template | a reusable interface shape | sections and fields |

Each artifact belongs to a **tenant → environment → application** scope, and moves
through a lifecycle: `DRAFT → PENDING_REVIEW → PUBLISHED → DEPRECATED` (`StandardStatus`).

## Versioning (three axes)

DCC separates identity, compatibility and provenance — see [versioning.md](versioning.md):

1. **Content hash** — a deterministic SHA-256 of the canonical IR; identical content ⇒
   identical hash (`model@sha256:…`).
2. **Semantic version** — `major.minor.patch`, assigned from a field-level diff by
   `VersionBumpPolicy` (advisory; the maintainer confirms).
3. **History / lineage** — a Git-like DAG (`parent_hash`, `author`, `message`).

## Tenancy

Every tenant-scoped row carries a `tenant_id`, filtered per request by a Hibernate
filter. The caller's tenant comes from the authenticated token
(`dcc.security.tenant-claim`) and is bound for the duration of the use case. See the
tenant model in [configuration.md](configuration.md).

## Migration

A **migration order** promotes artifacts from one environment to a migration-target
environment (e.g. `PROD`), with an approval flow (`submit → approve → execute`), and is
itself tenant-scoped and versioned.

## Generation

From the central model, `dcc-generator` produces: Java **validation classes**,
request/response **DTO records**, PostgreSQL **DDL / Flyway SQL**, an **OpenAPI**
document, and a downloadable **JAR bundle**. Output is Thymeleaf-templated — see
[templating.md](templating.md).

## Exposure

The same business logic in `dcc-application` / `dcc-core` is exposed three ways:
a REST API (`dcc-web`, `/api/*`), a command-shaped API for the **CLI** and CI
(`/cli/<version>/*`), and an **MCP** server for AI assistants (`dcc-mcp`).
