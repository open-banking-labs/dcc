# ADR 0002: Retention (Logical Deletion) and an Append-Only Audit Trail

- Status: Accepted
- Date: 2026-10-16

## Context

DCC manages banking metadata (data standards, table structures, interface definitions,
templates) and is subject to a regulatory requirement that business records are never
physically deleted and that every change is attributable and reconstructable.

Two facts shaped the design:

- Every persistent entity extends `BaseEntity` / `TenantScopedEntity`, all use scalar
  foreign-key identifiers (no JPA associations) and all carry business-key uniqueness
  constraints (e.g. `uk_standard_scope_code`).
- The version tables already model *content* history (immutable snapshots keyed by content
  hash), which is a different concern from *operation* history: they answer "what did the
  artifact contain", not "who changed this row, when and why".

## Decision

1. **Logical deletion is a first-class retention feature.** `BaseEntity` owns
   `deleted_at` / `deleted_by` / `deletion_reason`, a `markDeleted(actor, reason)` method
   and a Hibernate `@SQLRestriction("deleted_at is null")`, so deleted rows disappear from
   every default query while remaining physically present for their retention period.
   Services never call `delete`; they mark rows deleted.

2. **Business-key uniqueness applies to live rows only.** The affected unique constraints
   are converted to partial unique indexes (`WHERE deleted_at IS NULL`), so a deleted
   record stops blocking reuse of its code while live duplicates are still rejected.

3. **Every create / update / delete is recorded in an append-only `audit_log`.** A JPA
   entity listener (`AuditListener`) captures a deterministic before/after image of the row
   and an `AuditRecorder` writes the entry just before the surrounding transaction commits,
   so the audit entry and the change it describes commit atomically. Entries are chained by
   a SHA-256 hash of the previous entry for the same row, which makes tampering or removal
   detectable.

4. **Version history and reference rows are retained, not cascaded away.** Logically
   deleting an artifact keeps its version snapshots and recorded standard references, so
   the deleted record stays reconstructable.

## Consequences

- Every entity table carries three extra columns; a new entity inherits them from
  `BaseEntity` automatically and is audited without further work.
- Physical deletion should never happen through the application. A `PHYSICAL_DELETE` audit
  action exists precisely so that a stray hard delete is observable and alertable.
- Freshness of the audit row depends on `ChangeContext` (the use-case layer supplies the
  actor and reason through the `AuditIdentityProvider` port); when absent the actor falls
  back to `system`.
- Restoring a logically-deleted row is supported for every deletable resource
  (`POST /api/{resource}/{id}/restore`, tenants by code, plus `POST /cli/v1/restore` with
  the admin authority). It can collide with a live row that reused the code; the database
  partial unique index is the arbiter and the restore fails with a `409 Conflict`.
- The audit trail is readable through `GET /api/audit` (filtered, paged search by
  entity/id/actor/action and time window) and `GET /api/audit/{entityType}/{entityId}`
  (full history), admin-only. The command API mirrors these as `POST /cli/v1/audit/*`
  and MCP exposes the same reads read-only (`dcc-audit-history`, `dcc-audit-search`,
  `dcc-audit-verify`).
- Tamper-evidence is operational, not just a data model: `AuditService.verify` recomputes
  every entry's hash against its stored canonical form (byte-exact `text` JSON columns —
  jsonb was rejected because PostgreSQL normalizes jsonb, which would break byte-exact
  re-verification) and checks the `previous_hash` linkage; exposed as
  `GET /api/audit/{entityType}/{entityId}/verify`. Removing a *middle* entry is detected;
  removing the tail is inherently undetectable from an entity-local chain.
- Every entry freezes its compliance cutoff (`retention_until`, from
  `dcc.audit.retention-years`, UTC calendar years) at write time; later policy changes do
  not rewrite history. A report-only scheduled sweep (`dcc.audit.sweep-enabled`) counts
  and flags entries past their cutoff for compliance reporting; nothing is purged until an
  explicitly approved process exists.
- Database-level hardening lives in migration
  `V20261016000300__audit_tenant_index_retention_and_immutability.sql`: the history index
  is tenant-scoped (`tenant_id, entity_type, entity_id, id`, plus a partial platform index),
  and `UPDATE`/`DELETE` on `audit_log` are revoked from `PUBLIC`.
- The audit trail is written by the application; the revoke above makes the trail
  immutable at the database level as well (the table owner retains rights for maintenance).
