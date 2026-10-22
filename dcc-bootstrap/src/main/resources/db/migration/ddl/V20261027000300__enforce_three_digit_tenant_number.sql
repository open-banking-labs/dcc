-- The tenant segment of generated identifiers is exactly three decimal digits.
-- Existing tenants keep their identity values; a deployment that already has more than
-- 999 tenants must switch to a wider segment (and adjust the identifier rules) first.
ALTER TABLE tenant
    ADD CONSTRAINT ck_tenant_id_3_digits CHECK (id BETWEEN 1 AND 999);
