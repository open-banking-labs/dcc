package cn.org.openbanking.dcc.security;

import java.util.ArrayList;
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
 *     # Role -> permission-point bundle (ADR 0005). Bundles are the supported way to
 *     # shape a role: endpoints check permission points (@perms.*), never role names.
 *     # An unknown role confers nothing. Defaults ship the six-role model.
 *     role-bundles:
 *       DCC_MAINTAINERS: [DCC_MAINTAINER]
 *     # Permission point -> authority string. Empty means "the name is the authority".
 *     permissions:
 *       artifact.write: DCC_ARTIFACT_WRITE
 *     cors:
 *       allowed-origins: []   # empty => CORS not configured
 * </pre>
 */
@ConfigurationProperties(prefix = "dcc.security")
public class DccSecurityProperties {

    public static final String TENANT_ADMIN_ROLE = "DCC_TENANT_ADMIN";
    public static final String PLATFORM_ADMIN_ROLE = "DCC_PLATFORM_ADMIN";

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
     * Permission point -&gt; authority string. An absent entry means the permission name
     * <em>is</em> the authority, so {@code @perms.artifactWrite} resolves to
     * {@code artifact.write} unless a deployment remaps it to its IdP's vocabulary.
     */
    private Map<String, String> permissions = new LinkedHashMap<>();

    /**
     * Role -&gt; permission points (ADR 0005). A role lists the permission names declared in
     * {@link DccPermissions}; {@link ConfigRolePermissionResolver} expands them, mapped through
     * {@link #permissions}. Only roles declared here confer permissions; an unknown role confers
     * nothing. The shipped defaults carry the six-role model.
     */
    private Map<String, List<String>> roleBundles = new LinkedHashMap<>();

    /**
     * The bundles actually in force: the configured ones when present, otherwise the shipped
     * defaults.
     *
     * <p>Deliberately "configure or take defaults" rather than "merge with defaults": Spring Boot's
     * map binder mutates the existing map, so a pre-populated default would silently survive an
     * explicit {@code role-bundles} block. Returning the shipped defaults only when the configured
     * map is empty lets a deployment replace them wholesale (notably for
     * {@code resolver-mode=database}).
     */
    public Map<String, List<String>> effectiveRoleBundles() {
        return roleBundles.isEmpty() ? defaultRoleBundles() : roleBundles;
    }

    /**
     * Roles a tenant administrator may grant inside its own tenant (ADR 0005 §D5). Platform roles
     * are deliberately absent: a directory row naming {@code DCC_PLATFORM_ADMIN} would otherwise
     * launder platform power through a tenant-scoped grant.
     */
    private List<String> tenantGrantableRoles = List.of(
            "DCC_READER", "DCC_MAINTAINER", "DCC_APPROVER", "DCC_ENV_ADMIN", TENANT_ADMIN_ROLE);

    /**
     * Roles that may only be granted for one environment or application, never tenant-wide
     * (ADR 0005 — the delegable "environment administrator"). A tenant-wide grant of such a role
     * would silently hand out the whole data plane.
     */
    private List<String> scopeRequiredRoles = List.of("DCC_ENV_ADMIN");

    /**
     * Privileged roles whose grants need a second person (dual control, ADR 0005 §D10). The grant is
     * written and audited immediately but confers nothing until a <em>different</em> subject confirms
     * it, so no single administrator can create a platform or tenant administrator.
     *
     */
    private List<String> dualControlRoles = List.of(PLATFORM_ADMIN_ROLE, TENANT_ADMIN_ROLE);

    /**
     * How roles become authorities: {@code config} (default) uses {@link #roleBundles};
     * {@code database} makes the platform-level {@code role_permission} table the only authority
     * (ADR 0005 §D8). See {@code GrantPolicy} for the closed-vocabulary consequence on granting.
     */
    private String resolverMode = "config";

    /**
     * Directory-backed identity governance. When on, a verified token is additionally
     * validated against the authorization directory (application layer):
     * the subject must hold a live membership in the claimed tenant, and roles assigned
     * in the directory are unioned with the token's claim roles. Needs a
     * {@link DirectoryAccessPolicy} bean, so it can only be enabled where the
     * application module is on the classpath (dcc-web / dcc-mcp). Default off keeps
     * the claim-only behaviour (useful when the IdP fully manages memberships).
     */
    private boolean directoryMode = false;

    /** Cross-origin configuration for the exposure layers. */
    private Cors cors = new Cors();

    /** Emergency access (break-glass), disabled while {@link BreakGlass#subjects} is empty. */
    private BreakGlass breakGlass = new BreakGlass();

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

    public Map<String, String> getPermissions() {
        return permissions;
    }

    public void setPermissions(Map<String, String> permissions) {
        this.permissions = permissions;
    }

    public Map<String, List<String>> getRoleBundles() {
        return roleBundles;
    }

    public void setRoleBundles(Map<String, List<String>> roleBundles) {
        this.roleBundles = roleBundles;
    }

    public List<String> getTenantGrantableRoles() {
        return tenantGrantableRoles;
    }

    public void setTenantGrantableRoles(List<String> tenantGrantableRoles) {
        this.tenantGrantableRoles = tenantGrantableRoles;
    }

    public List<String> getScopeRequiredRoles() {
        return scopeRequiredRoles;
    }

    public void setScopeRequiredRoles(List<String> scopeRequiredRoles) {
        this.scopeRequiredRoles = scopeRequiredRoles;
    }

    public List<String> getDualControlRoles() {
        return dualControlRoles;
    }

    public void setDualControlRoles(List<String> dualControlRoles) {
        this.dualControlRoles = dualControlRoles;
    }

    public String getResolverMode() {
        return resolverMode;
    }

    public void setResolverMode(String resolverMode) {
        this.resolverMode = resolverMode;
    }

    public boolean isDirectoryMode() {
        return directoryMode;
    }

    public void setDirectoryMode(boolean directoryMode) {
        this.directoryMode = directoryMode;
    }

    public Cors getCors() {
        return cors;
    }

    public void setCors(Cors cors) {
        this.cors = cors;
    }

    public BreakGlass getBreakGlass() {
        return breakGlass;
    }

    public void setBreakGlass(BreakGlass breakGlass) {
        this.breakGlass = breakGlass;
    }

    /**
     * Emergency access (ADR 0005 §D10): a handful of subjects may act with a fixed set of extra
     * permissions <em>for one request</em>, but only when they state a reason.
     *
     * <p>Two properties keep this from becoming a quiet super-user:
     * <ul>
     *   <li>it is off by default ({@code subjects} empty),</li>
     *   <li>it never bypasses the directory membership gate — break-glass widens what a known
     *       member may do, it does not open the tenant to an outsider.</li>
     * </ul>
     * Every use logs a {@code BREAK-GLASS} warning line, and the reason travels in
     * {@code X-DCC-Change-Reason}, which the exposure layers already replay into the append-only
     * audit trail — so "who did what, and why" survives the emergency.
     */
    public static class BreakGlass {

        /** Subjects allowed to invoke break-glass; empty disables the feature entirely. */
        private List<String> subjects = List.of();

        /**
         * Permission points (or authority literals) granted while break-glass is active. Keep this
         * list as small as the emergency procedure allows — it is not a role, and it is not scoped.
         */
        private List<String> permissions = List.of();

        /** Header that must carry a non-blank reason for break-glass to apply. */
        private String reasonHeader = "X-DCC-Change-Reason";

        public List<String> getSubjects() {
            return subjects;
        }

        public void setSubjects(List<String> subjects) {
            this.subjects = subjects;
        }

        public List<String> getPermissions() {
            return permissions;
        }

        public void setPermissions(List<String> permissions) {
            this.permissions = permissions;
        }

        public String getReasonHeader() {
            return reasonHeader;
        }

        public void setReasonHeader(String reasonHeader) {
            this.reasonHeader = reasonHeader;
        }
    }

    /** Every permission point declared by {@link DccPermissions} (ADR 0005 §D8). */
    private static final List<String> PERMISSION_NAMES = List.of(
            "artifact.read", "artifact.write", "artifact.delete", "artifact.restore",
            "artifact.transition", "migration.write", "migration.approve", "migration.execute",
            "generation.read", "generation.package", "topology.read", "topology.write",
            "directory.read", "directory.write", "directory.write.platform",
            "audit.business", "audit.platform", "case.read", "case.adopt", "case.write",
            "announcement.write", "announcement.write.platform",
            "tenant.admit");

    /** @return the declared permission-point catalog (ADR 0005 §D8). */
    public static List<String> permissionNames() {
        return PERMISSION_NAMES;
    }

    /**
     * The shipped role bundles: the six-role model (ADR 0005 §D9). Unknown roles confer nothing.
     */
    private static Map<String, List<String>> defaultRoleBundles() {
        List<String> reader = List.of("artifact.read", "generation.read", "topology.read");
        Map<String, List<String>> bundles = new LinkedHashMap<>();
        bundles.put("DCC_READER", reader);
        bundles.put("DCC_MAINTAINER", union(reader, "artifact.write", "artifact.delete",
                "migration.write", "generation.package"));
        bundles.put("DCC_APPROVER", union(reader, "artifact.transition", "migration.approve"));
        // delegated environment administrator: the tenant administrator's data-plane half, scoped to
        // one environment/application. Deliberately without approval (four-eyes stays with
        // DCC_APPROVER), without topology.write (environments stay the tenant administrator's) and
        // without directory/audit/case.
        bundles.put("DCC_ENV_ADMIN", union(reader, "artifact.write", "artifact.delete", "migration.write",
                "migration.execute", "generation.package"));
        bundles.put(TENANT_ADMIN_ROLE, union(reader, "artifact.restore", "migration.execute",
                "topology.write", "directory.read", "directory.write", "audit.business",
                "case.read", "case.adopt", "announcement.write"));
        bundles.put(PLATFORM_ADMIN_ROLE, List.of("tenant.admit", "directory.read",
                "directory.write.platform", "audit.platform", "case.write", "case.read",
                "announcement.write.platform"));
        return bundles;
    }

    private static List<String> union(List<String> base, String... extra) {
        List<String> all = new ArrayList<>(base);
        all.addAll(List.of(extra));
        return List.copyOf(all);
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
