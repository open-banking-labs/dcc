# Configuration

All DCC-specific configuration lives under the `dcc.*` prefix. Every property has a
code-level default that reproduces the built-in behaviour, so an application that sets
none of them is unaffected.

Environment-specific files: `application.yml`, `application-dev.yml`,
`application-prod.yml`, `application-local.yml` (per module).

## Generation (`dcc-generator`)

### `dcc.generator.*`

Controls the template-driven code/SQL generation (see [templating.md](templating.md)).

| Property | Default | Description |
| --- | --- | --- |
| `dcc.generator.template-dir` | `classpath:/templates/` | Root the `.tpl` templates are resolved from. `classpath:/…`, `file:/abs/path/`, or a plain path. User templates override built-ins. |
| `dcc.generator.package-prefix` | `""` | Optional prefix prepended to generated packages. |
| `dcc.generator.entity-suffix` | `""` | Suffix for generated entity classes. |
| `dcc.generator.repository-suffix` | `Repository` | Suffix for generated repository interfaces. |
| `dcc.generator.dto-suffix` | `Dto` | Suffix for generated (nested) DTO records. |
| `dcc.generator.validation-package` | `cn.org.openbanking.dcc.generated.validation` | Package generated validation classes are emitted into. |
| `dcc.generator.dto-package` | `cn.org.openbanking.dcc.generated.dto` | Package generated DTO records are emitted into. |
| `dcc.generator.bundle-group` | `cn.org.openbanking.dcc.generated` | Maven group id for the generated artifact bundle. |
| `dcc.generator.default-version` | `1.0.0` | Version used when a bundle/OpenAPI version is not supplied. |

```yaml
dcc:
  generator:
    template-dir: file:/etc/dcc/templates/
    dto-suffix: Dto
```

### `dcc.type-mapping.*` and `dcc.database.dialect`

Type mapping is configuration-driven and dialect-aware. The active strategy is chosen
by `dcc.database.dialect` (`@ConditionalOnProperty`); `postgresql` is the default and
applies when the property is absent.

| Property | Default | Description |
| --- | --- | --- |
| `dcc.database.dialect` | `postgresql` | Selects the `TypeMappingStrategy` (SQL dialect). |
| `dcc.type-mapping.java.<TYPE>` | built-in | Override the logical type → Java type mapping. |
| `dcc.type-mapping.openapi.<TYPE>` | built-in | Override the logical type → OpenAPI/JSON type. |
| `dcc.type-mapping.dialects.<dialect>.<TYPE>` | built-in | Override the logical type → physical SQL type for a dialect. |

Keys are the logical data-type name, upper-cased (e.g. `VARCHAR`, `NUMERIC`,
`DATETIME`). Anything not listed falls back to the built-in mapping — for SQL types
that means the type passes through unchanged.

Built-in defaults:

| Logical | Java | OpenAPI |
| --- | --- | --- |
| `INT`/`INTEGER`/`SMALLINT` | `Integer` | `integer` |
| `BIGINT`/`LONG` | `Long` | `integer` |
| `DECIMAL`/`NUMERIC`/`NUMBER` | `java.math.BigDecimal` | `number` |
| `BOOLEAN`/`BOOL` | `Boolean` | `boolean` |
| `DATE` | `java.time.LocalDate` | `string` |
| `TIMESTAMP`/`DATETIME`/`INSTANT` | `java.time.Instant` | `string` |
| _(anything else)_ | `String` | `string` |

```yaml
dcc:
  database:
    dialect: postgresql
  type-mapping:
    dialects:
      postgresql:
        DATETIME: TIMESTAMP
      mysql:
        TIMESTAMP: DATETIME
    java:
      MONEY: java.math.BigDecimal
```

Adding a dialect = one `TypeMappingStrategy` class (+ optional config), never a
`switch` over dialects. See `PostgreSqlTypeMappingStrategy` / `MySqlTypeMappingStrategy`.

## Tenant model (`dcc-application`)

### `dcc.tenant.*`

The caller's tenant is decided by `dcc-security` from the token
(`dcc.security.tenant-claim`) and carried through the use-case layer; these record the
platform defaults and the targeted isolation level.

| Property | Default | Description |
| --- | --- | --- |
| `dcc.tenant.default-tenant` | `default_tenant` | Tenant id used where no caller tenant applies (seed data, system jobs). |
| `dcc.tenant.system-operator` | `system` | Author recorded for changes not made by an authenticated subject. |
| `dcc.tenant.isolation` | `ROW` | Targeted isolation: `ROW` \| `TABLE` \| `SCHEMA`. Only `ROW` (a Hibernate filter on `tenant_id`) is implemented today. |
| `dcc.tenant.enforce-tenant-status` | `true` | Every tenant-scoped use case revalidates the registry: requests whose tenant is disabled or logically deleted fail with `tenant_inactive` (HTTP 403). Turn off only when the token issuer is the sole gatekeeper. |

The **version strategy** (semantic versioning) is code, not config: `VersionBumpPolicy`
classifies a field-level diff as MAJOR/MINOR/PATCH and `SemVer.bump` produces the next
version — see [versioning.md](versioning.md).

## API path version (`dcc-web`)

### `dcc.api.*`

| Property | Default | Description |
| --- | --- | --- |
| `dcc.api.version` | `v1` | Path version of the `/cli/<version>` command API. `dcc-cli` must use the same value (via `DCC_API_VERSION`). |

## Excel export (`dcc-application`, worker in `dcc-web`)

### `dcc.export.*`

Asynchronous data-standard export. A request enqueues an `export_job` row; the worker
(`dcc.export.worker-enabled=true`, set only in `dcc-web`) writes the workbook to a temp
file, uploads it to S3-compatible storage, and the API returns a short-lived pre-signed
link. The application never streams the file itself. See [api.md](api.md).

| Property | Default | Description |
| --- | --- | --- |
| `dcc.export.worker-enabled` | `false` | Enables the scheduled worker. `dcc-web` sets it to `true`; `dcc-mcp` leaves it off. |
| `dcc.export.worker-delay-ms` | `2000` | Delay between queue-polling passes. |
| `dcc.export.cleanup-cron` | `0 15 * * * *` | Cron that deletes objects past their retention window and marks their jobs expired. |
| `dcc.export.stale-after-minutes` | `30` | RUNNING jobs older than this are failed on startup (crash recovery). |
| `dcc.export.presign-ttl` | `PT10M` | Lifetime of the generated download link. |
| `dcc.export.retention` | `PT24H` | How long the generated object stays in the bucket. |
| `dcc.export.max-rows` | `50000` | Hard cap on one export; larger results fail with a clear message. |
| `dcc.export.max-selected` | `100` | Hard cap on explicitly selected ids in one export/reference-analysis request. |
| `dcc.export.import-max-rows` | `2000` | Hard cap on one Excel import; all rows are validated before any write. |
| `dcc.export.page-chunk-size` | `500` | Rows fetched per page while streaming an export. |
| `dcc.export.s3.endpoint` | `http://localhost:9000` | S3-compatible endpoint; blank uses the AWS default. |
| `dcc.export.s3.region` | `us-east-1` | Signing region. |
| `dcc.export.s3.access-key` / `.secret-key` | MinIO defaults / empty | Static credentials; blank keys fall back to the default AWS credentials chain. |
| `dcc.export.s3.bucket` | `dcc-exports` | Private bucket; auto-created on first upload. |
| `dcc.export.s3.path-style` | `true` | Path-style addressing (required by MinIO). |

Local development uses the optional MinIO service (`./scripts/dcc-start.sh --minio`).
The bucket is private; the pre-signed URL carries `response-content-disposition:
attachment`. If the storage is unreachable the export job becomes `FAILED` with the
error exposed by `GET /api/standards/exports/{id}`; the rest of the console is unaffected.
The `docker-compose.yml` `dcc-app` service receives `DCC_EXPORT_S3_*` from the
`MINIO_ROOT_USER`/`MINIO_ROOT_PASSWORD` values; a host-run backend reads them after
`set -a; source env/dev.env; set +a`.

## Audit trail (`dcc-core`)

### `dcc.audit.*`

| Property | Default | Description |
| --- | --- | --- |
| `dcc.audit.retention-years` | `7` | Compliance retention period. Recorded per entry as `audit_log.retention_until` when it is written (UTC calendar years from `occurred_at`), so later changes here never rewrite the cutoff of existing entries. The application never purges; see [decisions/0002](decisions/0002-retention-and-audit-trail.md). |
| `dcc.audit.sweep-enabled` | `false` | Enables the scheduled audit retention sweep. Report-only: it counts and logs entries past their cutoff (and `AuditRetentionSweeper.pastRetention()` is usable programmatically); nothing is removed. |
| `dcc.audit.sweep-cron` | `0 0 3 * * *` | Cron expression for the retention sweep. |

## Security (`dcc-security`)

### `dcc.security.*`

| Property | Default | Description |
| --- | --- | --- |
| `dcc.security.jwt-secret` | dev-only | HS256 secret used to verify bearer tokens when no JWKS is set. Override via `DCC_JWT_SECRET`. |
| `dcc.security.jwk-set-uri` | _(unset)_ | JWKS URI for asymmetric tokens; takes precedence over `jwt-secret`. |
| `dcc.security.tenant-claim` | `tenant` | JWT claim carrying the tenant id. |
| `dcc.security.roles-claim` | `roles` | JWT claim carrying the caller's roles. |
| `dcc.security.permit-paths` | health/docs paths | Request paths served without authentication. |
| `dcc.security.permissions.<permission>` | the name itself | Remaps a permission point (ADR 0005) to an authority string, e.g. `artifact.write: DCC_ARTIFACT_WRITE`. Unset means the permission name *is* the authority. |
| `dcc.security.role-bundles.<role>` | six roles | Role → permission points (ADR 0005). An entry may name a permission point or another role (IdP group → role composition). Defaults ship `DCC_READER` / `DCC_MAINTAINER` / `DCC_APPROVER` / `DCC_ENV_ADMIN` / `DCC_TENANT_ADMIN` / `DCC_PLATFORM_ADMIN`. **Configure or take defaults**: any `role-bundles` block replaces the shipped defaults wholesale. A role with no bundle confers nothing. An entry that is neither a known role nor a declared permission point fails at startup. |
| `dcc.security.tenant-grantable-roles` | `DCC_READER`, `DCC_MAINTAINER`, `DCC_APPROVER`, `DCC_ENV_ADMIN`, `DCC_TENANT_ADMIN` | Roles a tenant administrator may grant inside its own tenant (`POST /api/role-assignments`). Platform roles are deliberately excluded, so a directory row cannot launder platform power. |
| `dcc.security.scope-required-roles` | `DCC_ENV_ADMIN` | Roles that may only be granted for one environment or application. A tenant-wide grant of these is rejected (422): it would hand out the whole data plane instead of one surface. |
| `dcc.security.dual-control-roles` | `DCC_PLATFORM_ADMIN`, `DCC_TENANT_ADMIN` | Grants of these roles are written and audited immediately but confer nothing until a **different** subject confirms them (`POST /api/directory/role-assignments/{id}/confirm`). Needs at least two platform administrators. |
| `dcc.security.resolver-mode` | `config` | `config` uses `role-bundles`; `database` makes the platform-level `role_permission` table the only authority — see ADR 0005 §D8. |
| `dcc.security.break-glass.subjects` | `[]` | Subjects allowed emergency access; empty disables the feature. |
| `dcc.security.break-glass.permissions` | `[]` | Permission points granted while break-glass is active (keep this list minimal). An unknown name fails at startup, and the effective set is logged when break-glass is enabled. |
| `dcc.security.break-glass.reason-header` | `X-DCC-Change-Reason` | Header that must carry a non-blank reason, or break-glass does not apply. That reason is replayed into the audit trail by the exposure layers. |
| `dcc.security.directory-mode` | `false` | Directory-backed identity governance. The token becomes identity-only: a verified subject may act only in tenants where `tenant_membership` grants it (otherwise `403 not_a_directory_member`), and roles from `role_assignment` are unioned with the claim roles before authority mapping. Requires the authorization directory (see `POST /api/directory/*`, ADR 0003); a directory-mode deployment without its `DirectoryAccessPolicy` bean fails at startup. Tenant administrators grant inside their own tenant via `POST /api/memberships` / `POST /api/role-assignments`; platform bootstrap stays on `/api/directory/*` (ADR 0005). |
| `dcc.security.cors.allowed-origins` | `[]` | Allowed origins; empty disables CORS. |
| `dcc.security.cors.allowed-methods` / `.allowed-headers` / `.allow-credentials` | GET/POST/…, `*`, `false` | CORS method/header/credentials settings. |

```yaml
dcc:
  security:
    # directory governance: the token is an identity, the directory grants access
    directory-mode: true
    # IdP groups can compose the built-in roles; unknown roles confer nothing
    role-bundles:
      DCC_OPS_ADMINS: [DCC_PLATFORM_ADMIN]
      DCC_TENANT_ADMINS: [DCC_TENANT_ADMIN]
    cors:
      allowed-origins: ["https://dcc.example.com"]
```

Directory bootstrap: create the first directory admin before enabling the mode (classic
claim-based tokens still work while it is off):

```bash
curl -X POST http://localhost:8080/api/directory/memberships \
  -H "Authorization: Bearer $DCC_TOKEN" -H "Content-Type: application/json" \
  -d '{"subject":"ops.admin","tenantCode":"default_tenant"}'
curl -X POST http://localhost:8080/api/directory/role-assignments \
  -H "Authorization: Bearer $DCC_TOKEN" -H "Content-Type: application/json" \
  -d '{"subject":"ops.admin","tenantCode":"default_tenant","role":"DCC_TENANT_ADMIN"}'
```

> The directory never removes what the identity provider grants. Roles from both sources
> are expanded through `role-bundles`; unknown role names confer nothing. Directory rows
> are audited and logically deleted (retention) like every other row.

Role assignments may set `scopeType` to `TENANT` (default), `ENVIRONMENT`, or
`APPLICATION`. Scoped assignments require an existing tenant-owned `scopeCode` and are
activated only when `X-DCC-Scope` and the request's explicit `environmentId` or
`applicationId` resolve to the same persisted target. For example:
`X-DCC-Scope: environment=DEV;application=core`. Opaque artifact-ID endpoints remain
tenant-wide and do not activate scoped grants (ADR 0004).

## Warm-up (`dcc-bootstrap`)

### `dcc.warmup.*`

| Property | Default | Description |
| --- | --- | --- |
| `dcc.warmup.enabled` | `true` | Run the warm-up at all. |
| `dcc.warmup.async` | `false` | Run off the startup thread. |
| `dcc.warmup.fail-fast` | `true` | On failure, stop the application versus log and continue. |
| `dcc.warmup.steps` | `[]` | Ordered list of `WarmupTask` names to run. |

## Migration (`dcc-application`)

### `dcc.migration.*`

| Property | Default | Description |
| --- | --- | --- |
| `dcc.migration.target-environments` | `[PROD]` | Environments that are migration-target-only. |

## MCP prompts (`dcc-mcp`)

### `dcc.mcp.prompts.*`

Externalized prompt templates (Thymeleaf, TEXT mode — see [templating.md](templating.md)).

| Property | Default | Description |
| --- | --- | --- |
| `dcc.mcp.prompts.dir` | `classpath:/mcp/prompts/` | Root of the prompt templates (`*.md.tpl`); a custom dir overrides built-ins. |
| `dcc.mcp.prompts.default-locale` | `en` | Locale used when a prompt request carries none. |

Prompt tool definitions (`name`, `description`, arguments) remain `@McpPrompt`/`@McpArg`
annotations — the MCP annotation scanner registers them — while the **text** is fully
externalized and localized.

## Database schema (Flyway)

Schema management is owned by `dcc-web` / the deployment and configured under
`spring.flyway.*` in `dcc-web/src/main/resources/application.yml`. See
[docs/flyway/](flyway/) for naming and rollback rules.

## Secrets

Do not commit real credentials. Bind secrets from the environment, e.g.
`DCC_JWT_SECRET` (and `env/.env.example`). See the project `AGENTS.md`.
