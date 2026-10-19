# dcc-security

`dcc-security` provides the cross-cutting security shared by every exposure layer, so authentication, tenancy and rate limiting are built once and reused by `dcc-web` and `dcc-mcp`.

- **Authentication** (`BearerJwtAuthenticationFilter`): verifies the `Authorization: Bearer <jwt>` token and puts an `InternalIdentity` (subject, tenant, roles) into the Spring Security context.
- **Token verification** (`JwtDecoder`): verifies with the identity provider's JWKS when `dcc.security.jwk-set-uri` is set, otherwise with an HS256 shared secret (`dcc.security.jwt-secret`) for development.
- **Tenant context** (`TenantContext`): the request's tenant, available to persistence and business code without threading it through every call.
- **Method authorization**: `@EnableMethodSecurity` is enabled, so domain-aware rules live next to the code they guard via `@PreAuthorize`.
- **Authorization model** (`DccPermissions` + `RolePermissionResolver`, ADR 0005): endpoints check a **permission point**, never a role name — `@PreAuthorize("hasAuthority(@perms.artifactWrite)")`. The 21 permission points are declared by `DccPermissions` (bean `@perms`) and can be remapped to the IdP's vocabulary under `dcc.security.permissions.*`. Roles are bundles of permission points: `dcc.security.role-bundles.<role>` (an entry may name a permission point or another role, so an IdP group can simply name a role). `RolePermissionResolver` unions claim roles and directory assignments, then expands each through its bundle. **A role without a bundle confers no authority.**
- **The six roles** (ADR 0005): `DCC_READER` (read + read-only generations), `DCC_MAINTAINER` (write, delete draft, submit, create migration order), `DCC_APPROVER` (publish/return, approve/reject), `DCC_ENV_ADMIN` (data plane delegated to one environment/application), `DCC_TENANT_ADMIN` (environments/applications, members and roles inside its own tenant, retention restore, execute approved migrations, business audit, adopt reference cases), `DCC_PLATFORM_ADMIN` (admit tenants, bootstrap the first tenant administrator, maintain the reference-case library, platform audit — and no data-plane permission at all). Roles are deliberately **not** hierarchical.
- **Effective permissions** (`GET /api/me`): the server-side union of claim roles, directory assignments and bundles, so the console stops deriving menus from the raw `roles` claim.
- **No legacy compatibility**: `DCC_ADMIN` and `DCC_USER` are removed. They are not shipped as bundles, there is no pass-through for unknown roles, and `DccAuthorities`, `role-authorities` and `roles-passthrough` no longer exist. Tokens or directory rows that still carry those names confer nothing.
- **Authorization directory** (`DirectoryAccessPolicy` + `dcc.security.directory-mode`, ADR 0003/0004): when enabled, the verified token is identity-only — `BearerJwtAuthenticationFilter` consults the application-backed policy for a live membership in the claimed tenant (`403 not_a_directory_member`) and unions tenant-wide plus validated environment/application roles with the claim roles before `RolePermissionResolver`. Scoped roles require a matching `X-DCC-Scope` and explicit target ID. The policy is a seam; this module stays free of JPA.
- **CORS**: applied only when `dcc.security.cors.allowed-origins` is set (empty by default, so no CORS is added).
- **Operation rate limiting** (`@OperationRateLimit` + `OperationRateLimitAspect`): a Redis fixed-window counter for expensive or abuse-prone operations - the fine-grained complement to the gateway's coarse, subject-level limit.

## Configuration (`dcc.security.*`)

| Property | Default | Meaning |
| --- | --- | --- |
| `jwt-secret` | dev-only value | HS256 secret used when no JWKS is configured |
| `jwk-set-uri` | *(unset)* | JWKS URI; takes precedence over `jwt-secret` |
| `tenant-claim` | `tenant` | JWT claim holding the tenant id |
| `roles-claim` | `roles` | JWT claim holding roles |
| `permit-paths` | health / API docs | Paths served without authentication |
| `permissions.<permission>` | the name itself | Remaps a permission point to an authority string (ADR 0005). |
| `role-bundles.<role>` | six roles | Role → permission points; entries may name a permission point or another role. Configured bundles replace the defaults wholesale; an unknown role confers nothing. |
| `resolver-mode` | `config` | `database` makes the platform-level `role_permission` table the only authority. |
| `scope-required-roles` | `DCC_ENV_ADMIN` | Roles that must be granted per environment/application, never tenant-wide. |
| `dual-control-roles` | `DCC_PLATFORM_ADMIN`, `DCC_TENANT_ADMIN` | Grants of these stay inert until a different subject confirms them. |
| `break-glass.subjects` / `.permissions` / `.reason-header` | `[]` / `[]` / `X-DCC-Change-Reason` | Emergency access: configured subjects get the listed permission points for one request, only with a stated reason; never bypasses the membership gate. |
| `tenant-grantable-roles` | reader/maintainer/approver/tenant-admin | What a tenant administrator may grant inside its own tenant. |
| `directory-mode` | `false` | Token is identity-only: membership gate + directory role union (ADR 0003). Requires a `DirectoryAccessPolicy` bean; startup fails without one when enabled. |
| `cors.allowed-origins` | `[]` | Allowed origins; empty disables CORS |

### Scope note

`dcc-security` only **verifies** tokens (issued by the identity provider); it does not
issue or refresh them. So JWT expiry/refresh, password policy and login lockout are
the identity provider's concern and are intentionally not configured here.

## Trust boundary

The exposure layers publish no host ports; the edge gateway is the only entry. The bearer token is authoritative here (`dcc-web` / `dcc-mcp` verify it directly), so "who the caller is" is decided in exactly one place - the gateway does not need to validate it.
