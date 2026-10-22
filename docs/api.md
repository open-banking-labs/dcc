# API reference

DCC exposes the same business logic three ways. All requests are tenant-scoped and
require a bearer token (except the permit paths — health and API docs).

- **REST** (`dcc-web`, port 8080) — `/api/*`
- **Command API** (`dcc-web`) — `/cli/<version>/*`, consumed by `dcc-cli` and CI
- **MCP** (`dcc-mcp`, port 8081) — `/mcp`, for AI assistants

Authentication: `Authorization: Bearer <jwt>`, verified by `dcc-security`. The JWT
carries the tenant (`dcc.security.tenant-claim`) and roles (`dcc.security.roles-claim`)
claims. Tenant status is revalidated at every tenant-scoped use case: a token issued
before its tenant was disabled or logically deleted is rejected with `403
tenant_inactive` (`dcc.tenant.enforce-tenant-status`, see
[configuration.md](configuration.md)). With `dcc.security.directory-mode=true` the
token is identity-only: requests also need a live directory membership in the claimed
tenant (`403 not_a_directory_member` otherwise) and directory-assigned roles are
unioned with the claim roles — see ADR 0003. Interactive docs: `/swagger-ui.html`.

## REST groups (`/api/*`)

| Group | Base path | Purpose |
| --- | --- | --- |
| Standards | `/api/standards` | CRUD, paged console search (`GET /page`, `keyword` matches code or name), batch summaries (`GET /summaries`), versioning (history/diff/rollback/verify), lifecycle transition, references, asynchronous Excel export (`POST /exports`, `GET /exports/{id}`, `GET /import-template`, `POST /import-excel`) and the machine-oriented JSON import/export |
| Tables | `/api/tables` | CRUD, search, versioning, status, `GET /{id}/ddl` and `/{id}/ddl/alter` |
| Interfaces | `/api/interfaces` | CRUD, search, versioning, status |
| Templates | `/api/templates` | CRUD, search, versioning, status |
| Release | `/api/generation` | `GET /manifest` (发布清单), `/openapi`, `GET /release` (应用发布 JAR). Publishing is **application-scoped**; there is no per-interface / per-standard export |
| Migration | `/api/migrations` | `POST /application` (以应用为单位批量建单), search, `submit`, `approve`, `reject`, `execute`, `rollback` |
| Versioning | `/api/versioning` | `GET /{type}/{id}/hash`, `POST /{type}/hash`, `GET /{type}/{id}/diff`, `/{id}/bump`, `GET /drift` |
| Audit | `/api/audit` | tenant administrator (`audit.business`): `GET /` filtered+paged search (entity/id/actor/action, `from`/`to` windows), `GET /{entityType}/{entityId}` full history, `GET /{entityType}/{entityId}/verify` recomputed hash chain |
| Platform audit | `/api/platform/audit` | system administrator (`audit.platform`): the same filtered+paged search over platform-level rows (`tenant_id is null`: tenant admission, grants, reference cases) |
| Directory (platform) | `/api/directory` | system administrator, bootstrap only: cross-tenant `GET|POST /memberships`, `DELETE /memberships/{id}`, `POST /memberships/{id}/restore`; `GET|POST /role-assignments`, `DELETE|restore`; plus `GET /my-memberships` (any authenticated caller lists their own grants, backing the console tenant switcher) |
| Directory (tenant) | `/api/memberships`, `/api/role-assignments` | tenant administrator: grant/list/revoke members and roles **inside its own tenant** (the tenant is taken from the token and never from the body; only `dcc.security.tenant-grantable-roles` may be granted), plus `POST /role-assignments/{id}/confirm` for a pending privileged grant |
| Reference cases | `/api/cases` | `GET /` + `GET /{id}` (`case.read`), `POST /{id}/adopt` (`case.adopt`, creates a tenant draft); `POST` / `PUT /{id}` / `DELETE /{id}` / `POST /{id}/restore` are `case.write`, i.e. platform-only |
| Case adoptions | `/api/case-adoptions`, `/api/platform/case-adoptions/{caseId}` | tenant side (`case.read`): my copies, each flagged `stale` once the case moved on — copies are never rewritten; platform side (`case.write`): which tenants adopted which version, with no tenant artifact identifier |
| Identity | `/api/me` | the caller's own identity, tenant and effective permission points — the console's only source of truth for menu/button gating |
| Dashboard | `/api/dashboard/summary` | one aggregated read for the console workbench: artifact counts (total / pending review), migration counts per lifecycle state, the five most recent orders, a 24h audit count and the tenant registry. Open to any authenticated caller; each section is gated by its own permission point server-side and comes back `null` when the caller may not read it |
| Announcements | `/api/announcements` | banner feed (`GET`, any authenticated caller: platform + own-tenant announcements, enabled and inside their visibility window) and tenant authoring (`GET /managed`, `POST`, `PUT /{id}`, `DELETE /{id}` behind `announcement.write`; the owning tenant always comes from the token, never the body) |
| Platform announcements | `/api/platform/announcements` | system administrator (`announcement.write.platform`): the same CRUD over platform-wide announcements (`tenant_id is null`). The two scopes are disjoint at the query level — a tenant announcement is a 404 from here, and a platform one is a 404 from `/api/announcements` |
| Tenancy | `/api/tenants`, `/api/environments`, `/api/applications` | tenants: `tenant.admit` (platform); environments/applications: `topology.read` / `topology.write` (tenant) |
| Identifier sequences | `/api/platform/id-sequences` | platform administrator (`tenant.admit`): read-only `GET` of persisted sequence positions (`currentValue`, `maxValue`, `step`, `remaining`), optional `?prefix=artifact:` |

Create commands for standards, interfaces, templates, tenants, applications and
environments accept an omitted or blank `code` / `interfaceNo`; the server generates one
from the identifier rules. A supplied code is still validated and used as-is. Manually
supplied artifact codes that match the reserved generated format (for example
`S00100200000001`) are rejected with `422`, because they would collide with future
generated values; import, migration and reference-case adoption are treated as
system-propagated and may preserve such a code. Sequence exhaustion is a `409`.

The console reads data standards through `GET /api/standards/page` (server-side paging;
`keyword` is a case-insensitive fuzzy match on the code or the current version's name)
and fetches only the selected rows' summaries with `GET /api/standards/summaries?ids=…`.
Excel export is asynchronous: `POST /api/standards/exports` enqueues a job and returns
`202`; `GET /api/standards/exports/{id}` is polled until `READY`, at which point it
returns a short-lived pre-signed link to the file in S3-compatible storage (MinIO in
development). One tenant may have at most one queued/running export at a time. The
console's import flow downloads `GET /api/standards/import-template`, then uploads the
edited workbook to `POST /api/standards/import-excel` (multipart, at most 2000 rows);
all rows are validated before anything is written, and row errors are returned in the
result. The JSON `GET /api/standards/export` / `POST /api/standards/import` pair remains
for the CLI, MCP and other machine clients.

Authorization is by **permission point**, not by role name (ADR 0005): every handler carries
`@PreAuthorize("hasAuthority(@perms.<point>)")`, e.g. `artifact.write` for editing,
`artifact.delete` for the logical delete, `artifact.transition` for publish/return,
`migration.approve` for a migration approval, `migration.execute` for running an approved one,
`topology.write` for environments/applications, `tenant.admit` for tenant admission. Roles
(`DCC_READER` / `DCC_MAINTAINER` / `DCC_APPROVER` / `DCC_ENV_ADMIN` /
`DCC_TENANT_ADMIN` / `DCC_PLATFORM_ADMIN`)
are bundles of those points under `dcc.security.role-bundles.*` (plus the scoped
`DCC_ENV_ADMIN`). Unknown roles confer nothing; the removed `DCC_ADMIN` / `DCC_USER` names are
inert. A write without the required point is `403`, and the four-eyes rule additionally
rejects approving a change you submitted yourself (`409`). See
[decisions/0005-role-model-and-permission-points.md](decisions/0005-role-model-and-permission-points.md).

The **approval gates** are scope-aware: `POST /api/{resource}/{id}/status`,
`POST /api/migrations/{id}/{approve,reject}` and `POST /api/migrations/{id}/{execute,rollback}`
are authorized by `@scopeGuard`, which resolves the resource's real environment/application from
the row and accepts either a tenant-wide grant or a directory grant scoped to that surface. So a
`DCC_APPROVER` grant scoped to `UAT` can approve UAT changes only — no `X-DCC-Scope` header needed,
and a forged header cannot extend it. `PENDING_REVIEW` (submit for review) requires
`artifact.write`; every other target requires `artifact.transition`. The same guard covers the
opaque-id writes: `PUT /api/{resource}/{id}` and `POST /{id}/rollback` need `artifact.write`,
`DELETE /{id}` needs `artifact.delete` — all satisfiable by a grant scoped to the object's surface.
Restore stays tenant-wide by design (the row is logically deleted, so it has no visible surface).

Privileged grants are **dual-control**: a `DCC_PLATFORM_ADMIN` or `DCC_TENANT_ADMIN` grant is
recorded immediately but confers nothing until a different subject calls
`POST /api/directory/role-assignments/{id}/confirm` (platform) or
`POST /api/role-assignments/{id}/confirm` (tenant).

Deletes are **logical** (retention): `DELETE /api/{resource}/{id}` marks the row deleted,
keeps it (and its version history) for the retention period, and records the change in the
append-only audit trail. Each deletable resource also exposes
`POST /api/{resource}/{id}/restore` (tenants: `POST /api/tenants/{code}/restore`) to bring
it back; a restore fails with `409` if a live record already reused its code. See
[decisions/0002-retention-and-audit-trail.md](decisions/0002-retention-and-audit-trail.md).

Example:

```bash
curl -s http://localhost:8080/api/standards/1 \
  -H "Authorization: Bearer $DCC_TOKEN"
```

## Command API (`/cli/<version>/*`)

Slim, POST + JSON, shaped for the CLI and pipelines. Version defaults to `v1`
(`dcc.api.version`). See [cli.md](cli.md) for the full table.

| Endpoint | Body |
| --- | --- |
| `POST /cli/v1/hash` | `{type, modelId, version}` |
| `POST /cli/v1/diff` | `{type, modelId, fromVersion, toVersion}` |
| `POST /cli/v1/bump` | `{type, modelId, fromVersion, toVersion}` |
| `POST /cli/v1/validate` | `{type, modelId, version}` |
| `POST /cli/v1/export/java` \| `/sql` | `{type, modelId, version}` |
| `POST /cli/v1/drift` | `{environmentId, applicationId}` |
| `POST /cli/v1/audit/history` \| `/search` \| `/verify` | `{entityType, entityId, …}` (requires `audit.business`) |
| `POST /cli/v1/restore` | `{type, id}` (requires `artifact.restore`) |

## MCP

The MCP server registers tools via annotations. Representative tools:
`list-standards`, `get-standard`, `diff-standard`, `export-standards`,
`get-table`, `list-tables`, `diff-table`, `generate-table-ddl`,
`generate-validation`, `generate-dto`, `generate-openapi`, `dcc-hash`, `dcc-diff`,
`dcc-bump`, the read-only `dcc-audit-history` / `dcc-audit-search` / `dcc-audit-verify`
trail tools, plus migration/template/interface tools. The whole MCP endpoint requires
`artifact.read`; the audit tools additionally require `audit.business` (ADR 0005). Restores
are deliberately not an MCP tool: privileged writes flow through REST / the command API,
which enforce the same permission points. A template-driven prompt,
`review-change`, drafts a review checklist (see [templating.md](templating.md) and
[modules/dcc-mcp.md](modules/dcc-mcp.md)).

See the controllers under `dcc-web/src/main/java/.../web/*` and the tools under
`dcc-mcp/src/main/java/.../mcp/tool/*` for the authoritative, complete list.
