-- Scoped directory grants (ADR 0004): a role_assignment may narrow its grant to one
-- operating surface (an environment or an application, addressed by tenant-unique
-- business code). TENANT keeps the historical tenant-wide semantics.

ALTER TABLE role_assignment
    ADD COLUMN scope_type varchar(16) NOT NULL DEFAULT 'TENANT',
    ADD COLUMN scope_code varchar(64);

ALTER TABLE role_assignment
    ADD CONSTRAINT ck_role_assignment_scope_type
        CHECK (scope_type IN ('TENANT', 'ENVIRONMENT', 'APPLICATION')),
    ADD CONSTRAINT ck_role_assignment_scope_shape
        CHECK ((scope_type = 'TENANT' AND scope_code IS NULL)
            OR (scope_type <> 'TENANT' AND scope_code IS NOT NULL));

-- A grant is unique per (tenant, subject, role, scope, scope target); existing rows
-- keep their tenant-wide uniqueness shape (scope_code stays NULL there).
DROP INDEX IF EXISTS uk_role_assignment_triple;
DROP INDEX IF EXISTS ix_role_assignment_tenant_subject;
CREATE UNIQUE INDEX uk_role_assignment_triple
    ON role_assignment (tenant_code, subject, role, scope_type, scope_code)
    NULLS NOT DISTINCT
    WHERE deleted_at IS NULL;
CREATE INDEX ix_role_assignment_tenant_subject ON role_assignment (tenant_code, subject);
