# Rollback Operations Manual

How to undo a database change in DCC.

> **Read this first.** Flyway **Community Edition has no `undo` command** —
> `undo` is a paid Teams/Enterprise feature, and this project uses Community.
> There is no `U` script to pair with a `V` script. Recovery is achieved by
> applying *more* migrations, or by restoring a backup. Plan every migration
> with that in mind.

---

## Choose a strategy

| Situation | Strategy |
|-----------|----------|
| Bad change reached production | **A. Forward fix** (preferred) |
| Data corrupted or lost | **B. Point-in-time restore** |
| Local dev environment only | **C. Clean and re-migrate** |

---

## A. Forward fix (preferred)

Write a new migration that reverses the effect. This keeps history linear and
auditable, and works identically in every environment.

```
V20260903090000__revert_merchant_status_column.sql
```

- Dropping a column or table added by the bad migration is straightforward.
- Restoring *data* is not: if the bad migration overwrote values, the forward
  fix cannot recover them. That case needs strategy B.
- For a destructive change, split it across two releases — add the new column
  and dual-write first, drop the old one only after the change has proven
  itself. A dropped column cannot be un-dropped.

---

## B. Point-in-time restore

Use when data was lost or corrupted and a forward fix cannot reconstruct it.

1. **Stop the application** so nothing writes during the restore.
2. Restore the database to a timestamp immediately before the bad migration
   (`pg_restore` / `pg_basebackup` + WAL replay, per your backup policy).
3. Inspect `flyway_schema_history` — the failed migration's row is gone along
   with the restored state, because the restore rolled the table back too.
4. Redeploy, or apply a corrected migration.

Restoring a production database is a **data-loss decision**: everything written
after the restore point is discarded. Get sign-off before starting.

---

## C. Clean and re-migrate (development only)

```bash
# dev only — application-dev.yml sets clean-disabled: false
./mvnw spring-boot:run -pl dcc-starter -Dspring-boot.run.profiles=dev \
  -Dspring-boot.run.arguments=--spring.flyway.clean-on-validation-error=true
```

Or drop the volume and let the container recreate it:

```bash
docker-compose --env-file env/dev.env down -v
./scripts/dcc-start.sh
```

> **`clean` is disabled in production.**
> `application-prod.yml` sets `spring.flyway.clean-disabled: true`, so
> `flyway clean` — which drops every object in the schema — cannot run against
> prod even by mistake. Do not flip this setting.

---

## Recovering from a failed migration

A migration that fails part-way leaves a `failed` row in
`flyway_schema_history`, and Flyway refuses to proceed until it is resolved.

1. **Check whether the transaction rolled back.** PostgreSQL DDL is
   transactional, so a migration that failed inside its own transaction left no
   partial schema change. A migration containing `CREATE INDEX CONCURRENTLY` is
   the exception — it cannot run in a transaction and *can* leave a partial
   index.

2. **Inspect the wreckage.**
   ```sql
   SELECT version, description, success, installed_on
   FROM flyway_schema_history
   ORDER BY installed_rank DESC
   LIMIT 5;
   ```

3. **Clean up any partial objects by hand.**

4. **Repair the history table**, which removes the failed row:
   ```bash
   ./mvnw flyway:repair -pl dcc-starter -Dflyway.url=... -Dflyway.user=... -Dflyway.password=...
   ```

5. **Fix the script and redeploy.** If the script had already been applied
   successfully somewhere (for example in dev), do not edit it — write a new
   migration instead.

---

## Safety rails already in place

| Setting | Effect |
|---------|--------|
| `validate-on-migrate: true` | Refuses to start if an applied script was edited |
| `out-of-order: false` | Refuses a migration numbered below the latest applied |
| `clean-disabled: true` (prod) | Blocks `flyway clean` in production |
| `init-sqls: SET SESSION lock_timeout = '2s'` | A migration waiting on a lock fails in 2s instead of hanging |
| `connect-retries: 3`, `interval: 5` | Tolerates a briefly unavailable database at startup |

`FlywayCallbackHandler` re-applies the `lock_timeout` on `BEFORE_MIGRATE` and
logs every script as it runs, so the application log is the primary record of
what a deployment executed.
