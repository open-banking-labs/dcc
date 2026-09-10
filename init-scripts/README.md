# PostgreSQL init scripts

Mounted read-only into the Postgres container at
`/docker-entrypoint-initdb.d` (see `docker-compose.yml`).

Every `*.sql` / `*.sh` file here runs **once**, in alphabetical order, and
**only when the data volume is empty** — that is, on the very first start of a
fresh database.

Because of that, this directory is *not* the place for schema changes. Use
Flyway for those (see `docs/flyway/`). Keep this directory for things that must
exist before Flyway connects, such as:

- creating extensions (`CREATE EXTENSION IF NOT EXISTS pg_trgm;`)
- creating roles and granting privileges
- tuning parameters that cannot be set per-session

To re-run these against an existing volume, you must delete it — which destroys
all data:

```bash
docker-compose --env-file env/dev.env down -v
./scripts/dcc-start.sh
```

Name files with a numeric prefix so the ordering is explicit:
`01-extensions.sql`, `02-roles.sql`.
