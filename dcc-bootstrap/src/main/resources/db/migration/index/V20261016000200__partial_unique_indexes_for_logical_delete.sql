-- Turn the business-key unique constraints into partial unique indexes that only cover
-- live rows, so a logically-deleted record no longer blocks reusing its code.
--
-- A plain UNIQUE constraint would keep deleted rows in the index and reject a new row
-- with the same code; a partial index (WHERE deleted_at IS NULL) enforces uniqueness only
-- among the rows that are still in the default view.

ALTER TABLE tenant DROP CONSTRAINT uk_tenant_code;
CREATE UNIQUE INDEX uk_tenant_code ON tenant (code) WHERE deleted_at IS NULL;

ALTER TABLE tenant_environment DROP CONSTRAINT uk_env_tenant_code;
CREATE UNIQUE INDEX uk_env_tenant_code ON tenant_environment (tenant_id, code) WHERE deleted_at IS NULL;

ALTER TABLE tenant_application DROP CONSTRAINT uk_app_tenant_code;
CREATE UNIQUE INDEX uk_app_tenant_code ON tenant_application (tenant_id, code) WHERE deleted_at IS NULL;

ALTER TABLE data_standard DROP CONSTRAINT uk_standard_scope_code;
CREATE UNIQUE INDEX uk_standard_scope_code
    ON data_standard (tenant_id, environment_id, application_id, code) WHERE deleted_at IS NULL;

ALTER TABLE table_structure DROP CONSTRAINT uk_table_scope_code;
CREATE UNIQUE INDEX uk_table_scope_code
    ON table_structure (tenant_id, environment_id, application_id, code) WHERE deleted_at IS NULL;

ALTER TABLE interface_definition DROP CONSTRAINT uk_interface_tenant_env_no;
CREATE UNIQUE INDEX uk_interface_tenant_env_no
    ON interface_definition (tenant_id, environment_id, interface_no) WHERE deleted_at IS NULL;

ALTER TABLE interface_template DROP CONSTRAINT uk_template_scope_code;
CREATE UNIQUE INDEX uk_template_scope_code
    ON interface_template (tenant_id, environment_id, application_id, code) WHERE deleted_at IS NULL;
