-- Retention (logical-deletion) columns for every persistent entity.
--
-- Bank-grade compliance forbids physically deleting business records: a row is marked
-- as deleted instead of removed and stays retrievable for its retention period. These
-- three columns back BaseEntity.deletedAt / deletedBy / deletionReason; BaseEntity also
-- carries @SQLRestriction("deleted_at is null") so deleted rows leave the default view.

ALTER TABLE tenant
    ADD COLUMN deleted_at timestamp with time zone,
    ADD COLUMN deleted_by varchar(128),
    ADD COLUMN deletion_reason varchar(512);

ALTER TABLE tenant_environment
    ADD COLUMN deleted_at timestamp with time zone,
    ADD COLUMN deleted_by varchar(128),
    ADD COLUMN deletion_reason varchar(512);

ALTER TABLE tenant_application
    ADD COLUMN deleted_at timestamp with time zone,
    ADD COLUMN deleted_by varchar(128),
    ADD COLUMN deletion_reason varchar(512);

ALTER TABLE data_standard
    ADD COLUMN deleted_at timestamp with time zone,
    ADD COLUMN deleted_by varchar(128),
    ADD COLUMN deletion_reason varchar(512);

ALTER TABLE data_standard_version
    ADD COLUMN deleted_at timestamp with time zone,
    ADD COLUMN deleted_by varchar(128),
    ADD COLUMN deletion_reason varchar(512);

ALTER TABLE table_structure
    ADD COLUMN deleted_at timestamp with time zone,
    ADD COLUMN deleted_by varchar(128),
    ADD COLUMN deletion_reason varchar(512);

ALTER TABLE table_structure_version
    ADD COLUMN deleted_at timestamp with time zone,
    ADD COLUMN deleted_by varchar(128),
    ADD COLUMN deletion_reason varchar(512);

ALTER TABLE standard_reference
    ADD COLUMN deleted_at timestamp with time zone,
    ADD COLUMN deleted_by varchar(128),
    ADD COLUMN deletion_reason varchar(512);

ALTER TABLE interface_definition
    ADD COLUMN deleted_at timestamp with time zone,
    ADD COLUMN deleted_by varchar(128),
    ADD COLUMN deletion_reason varchar(512);

ALTER TABLE interface_version
    ADD COLUMN deleted_at timestamp with time zone,
    ADD COLUMN deleted_by varchar(128),
    ADD COLUMN deletion_reason varchar(512);

ALTER TABLE interface_template
    ADD COLUMN deleted_at timestamp with time zone,
    ADD COLUMN deleted_by varchar(128),
    ADD COLUMN deletion_reason varchar(512);

ALTER TABLE template_version
    ADD COLUMN deleted_at timestamp with time zone,
    ADD COLUMN deleted_by varchar(128),
    ADD COLUMN deletion_reason varchar(512);

ALTER TABLE migration_order
    ADD COLUMN deleted_at timestamp with time zone,
    ADD COLUMN deleted_by varchar(128),
    ADD COLUMN deletion_reason varchar(512);
