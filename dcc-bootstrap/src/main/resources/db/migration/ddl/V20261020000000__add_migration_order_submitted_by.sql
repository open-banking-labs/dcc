-- ADR 0005 §D4 (four-eyes): record who submitted a migration order for approval, so the approval
-- step can reject self-approval by the same subject. Artifacts do not need an equivalent column:
-- their submitter is the author of the immutable current version.
alter table migration_order
    add column if not exists submitted_by varchar(128);

comment on column migration_order.submitted_by is
    'Subject that submitted this order for approval (four-eyes principle, ADR 0005)';

-- Note: rows already in PENDING_APPROVAL when this migration runs have no recorded submitter (the
-- information was never captured), so four-eyes cannot apply to them. Re-submitting records it; new
-- orders are always stamped by MigrationService#submit.
