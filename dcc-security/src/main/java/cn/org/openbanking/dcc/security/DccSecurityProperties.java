package cn.org.openbanking.dcc.security;

import java.util.List;

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
}
