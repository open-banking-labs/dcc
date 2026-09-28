# dcc-bootstrap

`dcc-bootstrap` holds the shared runtime/bootstrap wiring reused by every runnable app (`dcc-web`, `dcc-mcp`). It carries no transport or business logic.

- **Flyway startup integration** (`cn.org.openbanking.dcc.flyway`): the migration strategy, the callback that logs each script and caps lock waits, and the migration scripts under `classpath:db/migration/**`.
- **Warm-up framework** (`cn.org.openbanking.dcc.warmup`): `WarmupApplicationRunner`, `WarmupProperties`, the `WarmupTask` / `WarmableCache` SPIs, and the Hikari / JVM-cache / Redis tasks - all driven by `dcc.warmup.*` (see [dcc-web](dcc-web.md) warm-up table).
- **Shared infrastructure conventions**: JDBC and Redis.

## Flyway ownership

Only the schema owner runs migrations. `dcc-web` leaves `spring.flyway.enabled` at its default (`true`); `dcc-mcp` sets it to `false` but still has Flyway on the classpath via this module, so its `FlywayConfig` / `FlywayCallbackHandler` beans are simply inert.
