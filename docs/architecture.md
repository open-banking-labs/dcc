# Architecture

DCC is a multi-module Maven reactor. The top-level [ARCHITECTURE.md](../ARCHITECTURE.md)
holds the narrative; this page indexes the architecture decisions and the module
boundaries.

## Module boundaries

| Module | Responsibility | Kind |
| --- | --- | --- |
| `dcc-core` | Domain model + JPA persistence (standards, tables, interfaces, templates, tenancy, versioning) | library |
| `dcc-generator` | Turn domain models into code/SQL/OpenAPI/JAR (Thymeleaf-templated) | library |
| `dcc-application` | Use cases / orchestration over `dcc-core` + `dcc-generator` | library |
| `dcc-security` | JWT verification, tenant context, authorization, operation rate limiting | library |
| `dcc-bootstrap` | Startup wiring: Flyway integration + warm-up framework | library |
| `dcc-web` | REST + command API (owns the schema / runs Flyway) | application |
| `dcc-mcp` | MCP server over the same logic (validates, never migrates) | application |
| `dcc-cli` | Command-line execution client over the command API | application |

Dependencies point inward: applications → `dcc-application` → `dcc-core` /
`dcc-generator`. `dcc-security` and `dcc-bootstrap` are cross-cutting libraries reused
by both applications. Module notes live under [modules/](modules/).

Runtime flow: the exposure layer authenticates the caller (`dcc-security`), then calls
a `dcc-application` use case, which binds the tenant and works through `dcc-core`
(repositories) and/or `dcc-generator` (artifacts). `dcc-web` owns the database schema
(Flyway); `dcc-mcp` only validates it.

## Decisions (ADRs)

- [0001 — Use PostgreSQL](decisions/0001-use-postgres.md)

Add a new decision as `docs/decisions/NNNN-<slug>.md` (context, decision, consequences).

## Cross-cutting notes

- **Templating** — [templating.md](templating.md)
- **Configuration** — [configuration.md](configuration.md)
- **Versioning model** — [versioning.md](versioning.md)
- **Flyway naming / rollback** — [flyway/](flyway/)
