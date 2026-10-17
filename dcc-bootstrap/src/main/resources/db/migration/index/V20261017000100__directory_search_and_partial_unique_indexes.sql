-- Uniqueness for the authorization directory, live rows only (same partial-index policy
-- as all business keys, see the retention migration): a logically-deleted membership or
-- assignment no longer blocks re-granting the very same pair. Lookup indexes serve the
-- directory policy's per-request queries (existsBySubjectAndTenantCode) and the
-- admin search endpoints.

CREATE UNIQUE INDEX uk_tenant_membership_subject
    ON tenant_membership (tenant_code, subject) WHERE deleted_at IS NULL;
CREATE INDEX ix_tenant_membership_tenant ON tenant_membership (tenant_code);
CREATE INDEX ix_tenant_membership_subject ON tenant_membership (subject);

CREATE UNIQUE INDEX uk_role_assignment_triple
    ON role_assignment (tenant_code, subject, role) WHERE deleted_at IS NULL;
CREATE INDEX ix_role_assignment_tenant_subject ON role_assignment (tenant_code, subject);
CREATE INDEX ix_role_assignment_role ON role_assignment (role);
