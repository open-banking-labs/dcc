-- Seed the persistent identifier sequences from existing data.
--
-- current_value is the last value already used, so the next allocation returns
-- current_value + 1. Tenants without matching codes/applications are left unseeded;
-- the allocator inserts the row lazily and starts at 1.

INSERT INTO id_sequence (sequence_key, current_value, step, max_value, updated_at)
SELECT 'tenant-code',
       COALESCE(MAX(CASE
           WHEN code ~ '^T[0-9]{3}$' THEN substring(code from 2)::bigint
           ELSE NULL
       END), 0),
       1, 999, now()
FROM tenant;

INSERT INTO id_sequence (sequence_key, current_value, step, max_value, updated_at)
SELECT 'application-no:' || tenant.id,
       COALESCE(MAX(application.app_no), 0),
       1, 999, now()
FROM tenant
LEFT JOIN tenant_application application ON application.tenant_id = tenant.code
GROUP BY tenant.id;

INSERT INTO id_sequence (sequence_key, current_value, step, max_value, updated_at)
SELECT 'environment-code:' || tenant.id,
       COALESCE(MAX(CASE
           WHEN environment.code ~ '^E[0-9]{3}$' THEN substring(environment.code from 2)::bigint
           ELSE NULL
       END), 0),
       1, 999, now()
FROM tenant
LEFT JOIN tenant_environment environment ON environment.tenant_id = tenant.code
GROUP BY tenant.id;
