# ADR 0006: Rule-based identifier factory with a persisted global sequence

## Status

Accepted.

## Context

Tenant, application, environment, data standard, interface and template codes were
either user-entered or generated ad hoc. We need one core mechanism that:

- generates identifiers from a configurable assembly rule (prefix, suffix, separator,
  parts, padding, radix, sequence source);
- works across multiple application instances without duplicate values;
- survives restarts and lets operators inspect how far a sequence has advanced;
- keeps the existing model of two distinct fields: a system-generated `id` and a
  user-customisable `code`;
- does not add a per-row shard column: every tenant-scoped table already carries
  `tenant_id`, which remains the database shard key.

## Decision

1. **Rule engine in `dcc-core`.**
   `IdRule` is an ordered list of parts (`LITERAL`, `TYPE`, `TENANT`, `APPLICATION`,
   `SEQUENCE`, `DATE`) with `prefix`, `suffix`, `separator`, width, radix and padding.
   `dcc.id.rules.*` overrides built-in rules wholesale; all rules are validated at
   startup. A rule change requires a restart so one sequence never emits two formats.

2. **Tenant and application segments.**
   The tenant segment is the existing three-digit `tenant.id` (1–999). Applications get
   a per-tenant `app_no` (1–999) because their global surrogate id is neither per-tenant
   nor bounded to three digits. Tenant/application/environment codes can be entered by
   the user or generated as `T###`, `A###`, `E###`.

3. **Artifact codes.**
   Data standards, interfaces and templates use
   `<type><tenant:3><application:3><sequence:8>` (for example
   `S00100200000001`). A blank code is generated; a supplied code is validated and used,
   except that the generated format is reserved. The artifact's surrogate `id` stays an
   internal key, so this change does not break every foreign key.

4. **Persisted sequences.**
   `id_sequence` stores `sequence_key`, `current_value`, `step` and `max_value`.
   `DbSequenceStore` reserves a block with an atomic PostgreSQL upsert in a
   `REQUIRES_NEW` transaction; `SegmentSequenceSource` hands values out from a local
   buffer. Multiple instances share the table and therefore never overlap. A restart
   skips unused values instead of reissuing them.

5. **Open-source adaptation.**
   The block allocation follows Meituan Leaf's segment approach; it is implemented
   in-house on Spring/JPA rather than adding a new runtime dependency. A
   `SequenceSource` SPI leaves room for a database sequence, Redis or Snowflake source
   without changing the assembly rules.

6. **Operations.**
   `GET /api/platform/id-sequences` (platform administrator) exposes the persisted
   high-water marks. There is no reset endpoint: resetting would violate the no-reuse
   invariant.

## Consequences

- Identifiers are globally unique, monotonically allocated and never recycled. Gaps are
  expected after a crash, restart or larger `step`; this is the price of not reusing.
- Cross-instance issuance order is not a strict total order when `step > 1`; the rule
  controls throughput versus gap size.
- The 3-digit constraint caps tenants and per-tenant applications at 999 each. A
  deployment that outgrows it must widen the segment and migrate the rules.
- `tenant_id` remains the tenant code and the shard key; the numeric tenant id is used
  for the generated prefix. Making the numeric id the physical shard key would require
  a separate security-context and data migration.
- Existing codes are not rewritten; new records use the factory.
