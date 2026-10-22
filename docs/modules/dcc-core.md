# dcc-core

`dcc-core` contains shared domain and persistence capabilities, including Spring Data JPA and PostgreSQL integration. Keep database entities, repositories and reusable domain behavior here. Avoid HTTP-specific DTOs or application startup code.

## Identifier factory

The `cn.org.openbanking.dcc.core.id` package is the single source of generated business
identifiers:

- `IdRule` / `IdPart` — declarative assembly rules (`prefix`, `suffix`, `separator`,
  `TYPE` / `TENANT` / `APPLICATION` / `SEQUENCE` / `DATE` parts, width, radix, padding).
- `IdProperties` / `IdRuleRegistry` — `dcc.id.rules.*` overrides merged over built-in
  defaults and validated once at startup.
- `IdFactory` — resolves the tenant/application segments and asks the rule's
  `SequenceSource` for a value before `IdAssembler` renders the final string.
- `DbSequenceStore` + `SegmentSequenceSource` — PostgreSQL-backed Leaf-segment
  adaptation: atomic block reservation in `REQUIRES_NEW`, local buffer, persisted high
  water mark in `id_sequence`. Values are unique and never reused; a restart skips
  unused values rather than rewinding.

The sequence state is database state, not process state, so every application instance
shares it and a restart continues the same global flow.
