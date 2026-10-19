# ADR 0004: Scoped Directory Grants (Environment / Application Operating Surface)

- Status: Accepted
- Date: 2026-10-17

## Context

ADR 0003 introduced the authorization directory with tenant-scoped grants. The foreseen
next trigger is finer scoping: the bank-realistic case is *promoting a subject to admin
inside one environment* (e.g. PROD-vs-DEV separation) without granting tenant-wide power.

Two facts define the design space:

- The function-level guard (`@PreAuthorize`) runs at the security layer, where the
  request carries no resource context: endpoints are `/api/standards/{id}` — the
  environment is a property of the row, not of the URL. Only `/search` takes optional
  `environmentId`/`applicationId` query parameters.
- The use-case layer knows the exact scope of every resource operation, but must not
  carry authority semantics (and duplicate the `@PreAuthorize` matrix).

## Decision

1. **Grants may carry an operating-surface scope.** `role_assignment` gains
   `scope_type` (`TENANT` — the current default and semantics, `ENVIRONMENT`,
   `APPLICATION`) and a nullable `scope_code` (the tenant-unique business code of the
   referenced environment/application). A tenant also uniquely identifies the target,
   so one grant names exactly one real operating surface.

2. **Applicability is declared and tied to an explicit target.** A grant applies to a request when
   * its tenant matches the token's tenant, **and**
   * `scope_type = TENANT` (always applies), or the request declares a matching scope
     in the standardized header `X-DCC-Scope` (`environment=DEV;application=core`),
     **and** the request carries the corresponding `environmentId` / `applicationId`.
     The application policy resolves that ID inside the claimed tenant and verifies
     that its persisted code equals the header code before returning any scoped role.
   Applicable grant roles are unioned with the tenant-wide grants and the claim roles.

3. **The header is a selection, not a claim.** The client sends its active operating
   surface; forging it cannot elevate anyone, because both the directory grant and the
   real tenant-owned target must match. Without the header or the explicit target ID,
   scoped grants are inert (safe default; claim-only mode ignores the header entirely).

4. **Enforcement stays where it is for explicitly-scoped operations.** Scoped grants
   are evaluated at the same place as every other authority source (the filter, after
   signature verification). Detail/update/delete endpoints that identify only an
   opaque artifact ID do **not** activate scoped grants; callers need a tenant-wide or
   claim role there until those endpoints gain resource-scope reverse lookup. This
   fail-closed boundary prevents a DEV header from authorizing a PROD artifact action.

## Consequences

- A request can perform one indexed lookup per declared scope after validating the
  referenced environment/application inside the claimed tenant.
- Audit pages display scoped grants verbatim (`scope_type` + `scope_code`); audit and
  retention behaviour is unchanged (ADR 0002).
- Uniqueness of live grants becomes `(tenant, subject, role, scope_type, scope_code)`
  with `NULLS NOT DISTINCT` (PostgreSQL 16), so a re-grant per scope is possible.
- The referenced environment/application is validated at grant time and again when a
  request activates the grant. Deleted targets make the grant inert while retaining
  its audited row.
- Known limitation: opaque-ID detail/update/delete operations remain tenant-wide. A
  later increment may reverse-resolve their resource scope before method authorization.
  **Resolved for the approval gates by ADR 0005**: `ResourceScopeResolver` + `@scopeGuard`
  reverse-resolve a resource's environment/application from the row before authorization, so
  publish / send-back / deprecate, migration approve/reject and migration execute/rollback are
  decidable per environment without a scope header. Delete/restore and detail reads remain
  tenant-wide (tracked in ADR 0005's remaining work).
- Future work (same ADR series): per-artifact-type roles, database-backed
  role→authority mapping, scoped-grant expiry.
