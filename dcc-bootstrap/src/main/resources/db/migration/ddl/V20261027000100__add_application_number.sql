-- Per-tenant three-digit application number used by identifier rules.
--
-- The existing tenant_application.id is a global surrogate and cannot serve as the
-- 3-digit application segment (it is not per-tenant and may exceed three digits). Add
-- the segment explicitly and backfill it in a deterministic order.

ALTER TABLE tenant_application ADD COLUMN app_no integer;

WITH numbered AS (
    SELECT id,
           row_number() OVER (PARTITION BY tenant_id ORDER BY id) AS rn
    FROM tenant_application
)
UPDATE tenant_application application
SET app_no = numbered.rn::integer
FROM numbered
WHERE application.id = numbered.id;

ALTER TABLE tenant_application ALTER COLUMN app_no SET NOT NULL;
ALTER TABLE tenant_application
    ADD CONSTRAINT ck_app_no_3_digits CHECK (app_no BETWEEN 1 AND 999);

-- Live rows only, consistent with every other business-key uniqueness rule.
CREATE UNIQUE INDEX uk_app_tenant_no
    ON tenant_application (tenant_id, app_no)
    WHERE deleted_at IS NULL;
