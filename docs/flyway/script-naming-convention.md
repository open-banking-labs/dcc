# Flyway Script Naming Convention

This document describes how migration scripts in `dcc-starter` must be named.
Every rule below is derived from the active configuration in
`dcc-starter/src/main/resources/application.yml` — if you change that file,
update this document with it.

---

## Where scripts live

| Location | Use for |
|----------|---------|
| `classpath:db/migration/ddl` | Tables, columns, constraints, types |
| `classpath:db/migration/dml` | Seed and reference data |
| `classpath:db/migration/function` | Functions, procedures, triggers |
| `classpath:db/migration/index` | Indexes and other performance objects |

All four are scanned. Flyway walks them in order, so a script in `ddl/` and one
in `index/` with the same version number is a conflict — keep versions unique
across all four directories.

Each directory contains a `.gitkeep` so the empty directory survives in Git.
**Do not delete these:** Flyway is configured with all four locations, and the
application will fail to start if a configured location is missing.

---

## File name format

```
V<version>__<description>.sql
```

| Part | Value | Source |
|------|-------|--------|
| Prefix | `V` | `spring.flyway.sql-migration-prefix` |
| Version | 14-digit `yyyyMMddHHmmss` | matches `spring.flyway.baseline-version: 20260901000000` |
| Separator | `__` (two underscores) | `spring.flyway.sql-migration-separator` |
| Description | lower_snake_case, verb-led | convention |
| Suffix | `.sql` | `spring.flyway.sql-migration-suffixes` |

### Examples

```
V20260901000000__create_tenant_table.sql
V20260901103000__add_merchant_index.sql
V20260902141500__seed_currency_reference_data.sql
```

Use a real timestamp, not a counter. Two developers working on separate
branches will then almost never collide, and the ordering stays chronological.

---

## Rules

1. **Never edit a migration that has already been applied.**
   `spring.flyway.validate-on-migrate: true` compares a checksum of every
   applied script against the file on disk. Editing one makes the application
   refuse to start. To change an applied migration, add a new one.

2. **Versions must increase.** `spring.flyway.out-of-order: false` means a
   script with a lower version than the latest applied one is rejected. If two
   branches merge out of order, renumber the newer script rather than flipping
   the setting.

3. **One logical change per file.** A migration that both adds a column and
   backfills it is hard to roll back — see
   [rollback-operations-manual.md](rollback-operations-manual.md).

4. **A description may not contain `__`.** The first `__` ends the version, so
   a second one makes the name ambiguous.

5. **UTF-8 only.** `spring.flyway.encoding: UTF-8`.

6. **`flyway_schema_history` is the ledger.** Never modify or delete rows in it
   outside the documented repair procedure.

---

## Placeholders

The following placeholders are substituted into scripts at migration time
(prefix `${`, suffix `}`):

| Placeholder | dev | prod |
|-------------|-----|------|
| `${tenant_id}` | `default_tenant` | `default_tenant` |
| `${sys_operator}` | `system` | `system` |
| `${env_tag}` | `dev` | `prod` |

Use them instead of hard-coded values so one script serves every environment:

```sql
INSERT INTO sys_tenant (tenant_id, created_by)
VALUES ('${tenant_id}', '${sys_operator}');
```

Note that `${env_tag}` differs between environments. A migration whose
behaviour depends on it will produce *different data* in dev and prod — prefer
guarding with it over branching on it.

---

## Baseline

`spring.flyway.baseline-version: 20260901000000` with
`baseline-on-migrate: true`. On a database that already contains objects, Flyway
records a baseline at that version and applies only scripts numbered above it.
The first migration written should therefore be greater than `20260901000000`.
