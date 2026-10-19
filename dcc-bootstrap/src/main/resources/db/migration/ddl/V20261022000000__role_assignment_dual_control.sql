-- ADR 0005 §D10 (dual control): a grant that confers a privileged role needs a second person.
--
-- The grant is written immediately (audited, retained) but stays *pending* — the directory policy
-- ignores a pending privileged grant — until a different subject confirms it. `confirmation_required`
-- is decided at grant time by `dcc.security.dual-control-roles` and stored on the row, so the policy
-- needs no configuration of its own.

alter table role_assignment
    add column if not exists granted_by varchar(128),
    add column if not exists confirmation_required boolean not null default false,
    add column if not exists confirmed_by varchar(128),
    add column if not exists confirmed_at timestamp with time zone;

comment on column role_assignment.granted_by is
    'Subject that created the grant (dual control: the confirmer must differ)';
comment on column role_assignment.confirmation_required is
    'True for privileged roles (dcc.security.dual-control-roles): the grant confers nothing until confirmed';
comment on column role_assignment.confirmed_by is
    'Second subject that confirmed the grant; null means still pending';
