# Concepts

DCC manages **metadata about data** — never the data itself. Everything is scoped to a
tenant and versioned.

## Identifiers and codes

Every aggregate has two distinct fields:

- **`id`** — system-generated and immutable. Tenants use their three-digit identity
  (`001`–`999`); applications also carry a per-tenant three-digit `appNo`.
- **`code`** — the human/business identifier, optionally entered by the user. When it is
  blank the identifier factory generates one.

Generated artifact codes use the format
`<type><tenant:3><application:3><sequence:8>`, with no separator by default:

| Artifact | Example |
| --- | --- |
| Data standard | `S00100200000001` |
| Interface | `I00100200000001` |
| Interface template | `M00100200000001` |
| Tenant code | `T001` |
| Application code | `A002` |
| Environment code | `E001` |

The 8-digit sequence is per type + tenant + application, persisted in `id_sequence` and
allocated in blocks. It never resets, so a value is never reused after a delete or a
rollback; gaps are allowed. The numeric tenant id continues to be used for the code
prefix, while `tenant_id` on every tenant-scoped row remains the shard key. Rules
(prefix/suffix/separator/parts/sequence source) are configurable under `dcc.id.*`; see
[configuration.md](configuration.md).

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

Migration is requested **per application** (`POST /api/migrations/application`), not by
hand-typing an artifact id: it creates one order per artifact of the application in the
source environment (artifacts already at the source version in the target are skipped).

## Release (应用发布)

Publishing is **application-scoped**, not per-artifact: one release of a
`环境 + 应用` bundles *all* its interfaces' request/response **DTO records**, the Java
**validation classes** of the data standards they use, and an **OpenAPI** document into a
single downloadable **JAR**. There is no per-interface / per-standard export — you publish
an application. Output is Thymeleaf-templated — see [templating.md](templating.md).

Data standards are **exported**, not generated one by one: `GET /api/standards/export`
returns the whole scope as JSON; validation classes are produced only inside a release
bundle. PostgreSQL **DDL / Flyway SQL** for table structures is generated from the table
artifact (`GET /api/tables/{id}/ddl`).

## Exposure

The same business logic in `dcc-application` / `dcc-core` is exposed three ways:
a REST API (`dcc-web`, `/api/*`), a command-shaped API for the **CLI** and CI
(`/cli/<version>/*`), and an **MCP** server for AI assistants (`dcc-mcp`).
