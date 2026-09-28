# dcc-security

`dcc-security` provides the cross-cutting security shared by every exposure layer, so authentication, tenancy and rate limiting are built once and reused by `dcc-web` and `dcc-mcp`.

- **Authentication** (`BearerJwtAuthenticationFilter`): verifies the `Authorization: Bearer <jwt>` token and puts an `InternalIdentity` (subject, tenant, roles) into the Spring Security context.
- **Token verification** (`JwtDecoder`): verifies with the identity provider's JWKS when `dcc.security.jwk-set-uri` is set, otherwise with an HS256 shared secret (`dcc.security.jwt-secret`) for development.
- **Tenant context** (`TenantContext`): the request's tenant, available to persistence and business code without threading it through every call.
- **Method authorization**: `@EnableMethodSecurity` is enabled, so domain-aware rules live next to the code they guard via `@PreAuthorize`.
- **Operation rate limiting** (`@OperationRateLimit` + `OperationRateLimitAspect`): a Redis fixed-window counter for expensive or abuse-prone operations - the fine-grained complement to the gateway's coarse, subject-level limit.

## Configuration (`dcc.security.*`)

| Property | Default | Meaning |
| --- | --- | --- |
| `jwt-secret` | dev-only value | HS256 secret used when no JWKS is configured |
| `jwk-set-uri` | *(unset)* | JWKS URI; takes precedence over `jwt-secret` |
| `tenant-claim` | `tenant` | JWT claim holding the tenant id |
| `roles-claim` | `roles` | JWT claim holding roles |
| `permit-paths` | health / API docs | Paths served without authentication |

## Trust boundary

The exposure layers publish no host ports; the edge gateway is the only entry. The bearer token is authoritative here (`dcc-web` / `dcc-mcp` verify it directly), so "who the caller is" is decided in exactly one place - the gateway does not need to validate it.
