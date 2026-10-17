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
[configuration.md](configuration.md)). Interactive docs: `/swagger-ui.html`.

## REST groups (`/api/*`)

| Group | Base path | Purpose |
| --- | --- | --- |
| Standards | `/api/standards` | CRUD, search, versioning (history/diff/rollback/verify), lifecycle transition, references, import/export |
| Tables | `/api/tables` | CRUD, search, versioning, status, `GET /{id}/ddl` and `/{id}/ddl/alter` |
| Interfaces | `/api/interfaces` | CRUD, search, versioning, status |
| Templates | `/api/templates` | CRUD, search, versioning, status |
| Generation | `/api/generation` | `GET /standards/{id}/validation`, `/interfaces/{id}/dto`, `/openapi`, `/jar` |
| Migration | `/api/migrations` | create, search, `submit`, `approve`, `reject`, `execute`, `rollback` |
| Versioning | `/api/versioning` | `GET /{type}/{id}/hash`, `POST /{type}/hash`, `GET /{type}/{id}/diff`, `/{id}/bump`, `GET /drift` |
| Audit | `/api/audit` | admin-only: `GET /` filtered+paged search (entity/id/actor/action, `from`/`to` windows), `GET /{entityType}/{entityId}` full history, `GET /{entityType}/{entityId}/verify` recomputed hash chain |
| Tenancy | `/api/tenants`, `/api/environments`, `/api/applications` | tenant/environment/application administration |

Privileged operations are guarded by method security: lifecycle transitions require the
*approver* authority, deletes require the *admin* authority — names configured under
`dcc.security.authorities.*` (see [configuration.md](configuration.md)).

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

## MCP

The MCP server registers tools via annotations. Representative tools:
`list-standards`, `get-standard`, `diff-standard`, `export-standards`,
`get-table`, `list-tables`, `diff-table`, `generate-table-ddl`,
`generate-validation`, `generate-dto`, `generate-openapi`, `dcc-hash`, `dcc-diff`,
`dcc-bump`, the read-only `dcc-audit-history` / `dcc-audit-search` / `dcc-audit-verify`
trail tools, plus migration/template/interface tools. Restores are deliberately not an
MCP tool: privileged writes flow through REST / the command API, which enforce the admin
authority. A template-driven prompt,
`review-change`, drafts a review checklist (see [templating.md](templating.md) and
[modules/dcc-mcp.md](modules/dcc-mcp.md)).

See the controllers under `dcc-web/src/main/java/.../web/*` and the tools under
`dcc-mcp/src/main/java/.../mcp/tool/*` for the authoritative, complete list.
