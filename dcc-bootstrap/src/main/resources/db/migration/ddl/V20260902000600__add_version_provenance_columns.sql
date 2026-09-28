-- Three-axis version provenance: content identity (content_hash), Git-like lineage
-- (parent_hash), and who/why (author, message). Applies to every versioned artifact.

ALTER TABLE data_standard_version
    ADD COLUMN content_hash varchar(71),
    ADD COLUMN parent_hash  varchar(71),
    ADD COLUMN author       varchar(128),
    ADD COLUMN message      varchar(1024);

ALTER TABLE table_structure_version
    ADD COLUMN content_hash varchar(71),
    ADD COLUMN parent_hash  varchar(71),
    ADD COLUMN author       varchar(128),
    ADD COLUMN message      varchar(1024);

ALTER TABLE interface_version
    ADD COLUMN content_hash varchar(71),
    ADD COLUMN parent_hash  varchar(71),
    ADD COLUMN author       varchar(128),
    ADD COLUMN message      varchar(1024);

ALTER TABLE template_version
    ADD COLUMN content_hash varchar(71),
    ADD COLUMN parent_hash  varchar(71),
    ADD COLUMN author       varchar(128),
    ADD COLUMN message      varchar(1024);

CREATE INDEX ix_standard_version_content_hash ON data_standard_version (tenant_id, content_hash);
CREATE INDEX ix_table_version_content_hash ON table_structure_version (tenant_id, content_hash);
CREATE INDEX ix_interface_version_content_hash ON interface_version (tenant_id, content_hash);
CREATE INDEX ix_template_version_content_hash ON template_version (tenant_id, content_hash);
