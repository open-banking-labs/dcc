# ADR 0003: Authorization Directory (Tenant Memberships and Role Assignments)

- Status: Accepted
- Date: 2026-10-17

## Context

DCC deliberately has no user/role database: identity is delegated — `dcc-security`
verifies the bearer JWT and trusts its `sub`, `tenant` and `roles` claims. That model
was accepted on two conditions, both of which can outgrow the claims alone:

1. **Cross-tenant role difference.** A subject legitimately working in several tenants
   with different powers in each (admin in tenant A, user in tenant B) cannot be
   expressed by a single global `roles` claim.
2. **The IdP cannot express it either.** Enterprise IdPs issue one token with one global
   roles claim; modelling every dynamically created DCC tenant as a separate IdP
   application is operationally unrealistic.

When both hold, the only remaining place to store who-may-do-what-per-tenant is inside
DCC itself. A full user/password system stays out of scope — authentication remains
100% delegated to the identity provider.

## Decision

1. **The token becomes identity-only; the directory decides access.** Two small
   platform-level tables (`tenant_membership`: subject ↔ tenant; `role_assignment`:
   subject + tenant + role) back a new seam, `DirectoryAccessPolicy`, consulted by
   `BearerJwtAuthenticationFilter` right after signature verification:
   * membership **gates** (no live membership in the claimed tenant → `403 not_a_directory_member`),
   * assignments **add** (unioned with the claim roles before `role-authorities` mapping —
     the directory can grant, it never overrides the IdP).

2. **Opt-in, default equal to the old behaviour.** `dcc.security.directory-mode`
   (default `false`) keeps claim-only semantics for deployments whose IdP fully manages
   memberships. Enabling it without the application-layer policy bean fails at startup.

3. **Same persistence discipline as business rows.** Both entities extend `BaseEntity`
   (audit listener, retention columns, `@SQLRestriction`), uniqueness is
   partial-unique-index over live rows, delete is a logical delete, restore is exposed
   through the REST admin surface. Directory rows are platform-global (no `tenant_id`),
   like `Tenant` itself.

4. **Management through REST only.** `/api/directory/*` is admin-guarded; MCP gets no
   directory tools (matches the "no privileged writes over MCP" rule of ADR 0002). The
   bootstrap of the very first directory admin happens while the mode is still off.

## Consequences

- Directory access costs one indexed lookup per request (membership) plus one small
  query for assignments, on tables that stay small; acceptable at the security boundary.
- Grant/revoke takes effect immediately (next request), no IdP round-trip.
- Audit entries for directory rows appear in the existing trail with entity types
  `TenantMembership` / `RoleAssignment`.
- Restores deliberately conflict with re-grants (409, partial unique index仲裁), so a
  historical row can never silently resurrect a revoked permission that now belongs to
  someone else's newer grant.
- Future work (not in this decision): a console management UI, per-environment or
  per-module authorities, and a database-backed `RolePermissionResolver` for
  role→authority mappings.
