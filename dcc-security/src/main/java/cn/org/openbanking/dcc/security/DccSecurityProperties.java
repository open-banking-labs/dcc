package cn.org.openbanking.dcc.security;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the shared in-process security layer, bound from
 * {@code dcc.security.*}.
 *
 * <pre>
 * dcc:
 *   security:
 *     # HS256 secret used to verify bearer tokens when no JWKS is configured.
 *     # Override outside dev, or set jwk-set-uri for an asymmetric IdP.
 *     jwt-secret: ${DCC_JWT_SECRET:dev-only-change-me-please-use-32-bytes}
 *     jwk-set-uri:            # optional; takes precedence over jwt-secret
 *     tenant-claim: tenant
 *     roles-claim: roles
 *     permit-paths: [...]
 *     # Logical authority names, referenced from @PreAuthorize as
 *     # @dccAuthorities.admin / .approver / .user — no literals in code.
 *     authorities:
 *       admin: DCC_ADMIN
 *       approver: DCC_APPROVER
 *       user: DCC_USER
 *     # Optional expansion of a JWT role into one or more authorities.
 *     # A role with no entry is used verbatim as its own authority.
 *     role-authorities:
 *       editor: [DCC_USER]
 *     cors:
 *       allowed-origins: []   # empty => CORS not configured
 * </pre>
 */
@ConfigurationProperties(prefix = "dcc.security")
public class DccSecurityProperties {

    /**
     * HS256 secret used to verify bearer tokens when {@link #jwkSetUri} is not
     * set. The default is for local development only and must be replaced
     * (at least 32 bytes) everywhere else.
     */
    private String jwtSecret = "dev-only-change-me-please-use-32-bytes";

    /**
     * JWKS URI used to verify asymmetric tokens from the identity provider. When
     * set, it takes precedence over {@link #jwtSecret}.
     */
    private String jwkSetUri;

    /** JWT claim carrying the tenant identifier. */
    private String tenantClaim = "tenant";

    /** JWT claim carrying the caller's roles. */
    private String rolesClaim = "roles";

    /** Request paths that are served without authentication. */
    private List<String> permitPaths = List.of(
            "/actuator/health",
            "/actuator/health/**",
            "/actuator/info",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html");

    /**
     * Logical authority name -&gt; actual authority string. Controllers reference the
     * logical names (e.g. {@code @dccAuthorities.admin}) so no authority literal is
     * hard-coded. Defaults preserve the historical {@code DCC_*} names.
     */
    private Map<String, String> authorities = new LinkedHashMap<>(Map.of(
            "admin", "DCC_ADMIN",
            "approver", "DCC_APPROVER",
            "user", "DCC_USER"));

    /**
     * Optional JWT role -&gt; authorities expansion. A role not present here is used
     * verbatim as its own authority (the historical behaviour).
     */
    private Map<String, List<String>> roleAuthorities = new LinkedHashMap<>();

    /** Cross-origin configuration for the exposure layers. */
    private Cors cors = new Cors();

    public String getJwtSecret() {
        return jwtSecret;
    }

    public void setJwtSecret(String jwtSecret) {
        this.jwtSecret = jwtSecret;
    }

    public String getJwkSetUri() {
        return jwkSetUri;
    }

    public void setJwkSetUri(String jwkSetUri) {
        this.jwkSetUri = jwkSetUri;
    }

    public String getTenantClaim() {
        return tenantClaim;
    }

    public void setTenantClaim(String tenantClaim) {
        this.tenantClaim = tenantClaim;
    }

    public String getRolesClaim() {
        return rolesClaim;
    }

    public void setRolesClaim(String rolesClaim) {
        this.rolesClaim = rolesClaim;
    }

    public List<String> getPermitPaths() {
        return permitPaths;
    }

    public void setPermitPaths(List<String> permitPaths) {
        this.permitPaths = permitPaths;
    }

    public Map<String, String> getAuthorities() {
        return authorities;
    }

    public void setAuthorities(Map<String, String> authorities) {
        this.authorities = authorities;
    }

    public Map<String, List<String>> getRoleAuthorities() {
        return roleAuthorities;
    }

    public void setRoleAuthorities(Map<String, List<String>> roleAuthorities) {
        this.roleAuthorities = roleAuthorities;
    }

    public Cors getCors() {
        return cors;
    }

    public void setCors(Cors cors) {
        this.cors = cors;
    }

    /** CORS settings; when {@link #allowedOrigins} is empty, CORS is not configured. */
    public static class Cors {

        /** Allowed origins; empty disables CORS entirely (the built-in default). */
        private List<String> allowedOrigins = List.of();

        /** Allowed HTTP methods. */
        private List<String> allowedMethods = List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");

        /** Allowed request headers. */
        private List<String> allowedHeaders = List.of("*");

        /** Whether to allow credentials. */
        private boolean allowCredentials = false;

        public List<String> getAllowedOrigins() {
            return allowedOrigins;
        }

        public void setAllowedOrigins(List<String> allowedOrigins) {
            this.allowedOrigins = allowedOrigins;
        }

        public List<String> getAllowedMethods() {
            return allowedMethods;
        }

        public void setAllowedMethods(List<String> allowedMethods) {
            this.allowedMethods = allowedMethods;
        }

        public List<String> getAllowedHeaders() {
            return allowedHeaders;
        }

        public void setAllowedHeaders(List<String> allowedHeaders) {
            this.allowedHeaders = allowedHeaders;
        }

        public boolean isAllowCredentials() {
            return allowCredentials;
        }

        public void setAllowCredentials(boolean allowCredentials) {
            this.allowCredentials = allowCredentials;
        }
    }
}
