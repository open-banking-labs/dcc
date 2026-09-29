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

## Security (`dcc-security`)

### `dcc.security.*`

| Property | Default | Description |
| --- | --- | --- |
| `dcc.security.jwt-secret` | dev-only | HS256 secret used to verify bearer tokens when no JWKS is set. Override via `DCC_JWT_SECRET`. |
| `dcc.security.jwk-set-uri` | _(unset)_ | JWKS URI for asymmetric tokens; takes precedence over `jwt-secret`. |
| `dcc.security.tenant-claim` | `tenant` | JWT claim carrying the tenant id. |
| `dcc.security.roles-claim` | `roles` | JWT claim carrying the caller's roles. |
| `dcc.security.permit-paths` | health/docs paths | Request paths served without authentication. |

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

## Database schema (Flyway)

Schema management is owned by `dcc-web` / the deployment and configured under
`spring.flyway.*` in `dcc-web/src/main/resources/application.yml`. See
[docs/flyway/](flyway/) for naming and rollback rules.

## Secrets

Do not commit real credentials. Bind secrets from the environment, e.g.
`DCC_JWT_SECRET` (and `env/.env.example`). See the project `AGENTS.md`.
