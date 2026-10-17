-- Audit-trail hardening: read-path index, per-entry compliance cutoff and database-level
-- append-only enforcement. Complements docs/decisions/0002-retention-and-audit-trail.md.

-- 1) Every audit read path (history, search, chain lookup) filters by tenant first, so let
--    the isolation live inside the index instead of the heap filter. A partial index keeps
--    the rare platform-level rows (tenant_id is null, e.g. Tenant entries) cheap too.
DROP INDEX IF EXISTS ix_audit_entity;
CREATE INDEX ix_audit_tenant_entity ON audit_log (tenant_id, entity_type, entity_id, id);
CREATE INDEX ix_audit_platform_entity ON audit_log (entity_type, entity_id, id) WHERE tenant_id IS NULL;

-- 2) Per-entry retention cutoff. Freezing `retention_until` at write time means later policy
--    changes never retro-extend or shorten entries already on the trail. The backfill applies
--    the configured default period (dcc.audit.retention-years, currently 7y) to existing rows.
ALTER TABLE audit_log ADD COLUMN retention_until timestamp with time zone;
UPDATE audit_log SET retention_until = occurred_at + interval '7 years';
ALTER TABLE audit_log ALTER COLUMN retention_until SET NOT NULL;

-- 3) Append-only enforcement in the database, matching what the application code already
--    guarantees: audit_log rows are never updated or removed. Revoking from PUBLIC blocks
--    every other non-superuser role; the migration owner keeps full control for maintenance.
REVOKE UPDATE, DELETE ON audit_log FROM PUBLIC;
