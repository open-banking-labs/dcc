package cn.org.openbanking.dcc.security;

import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.stereotype.Component;

/**
 * The permission points the exposure layers authorize against (ADR 0005).
 *
 * <p>Endpoints check a <em>permission point</em>, not a role name:
 *
 * <pre>
 * &#64;PreAuthorize("hasAuthority(@perms.artifactWrite)")
 * </pre>
 *
 * <p>Roles are bundles of permission points ({@code dcc.security.role-bundles}), so adding or
 * re-cutting a role is configuration, not a code change across every controller. Each name is
 * itself the default authority string and can be remapped through {@code dcc.security.permissions.*}
 * when an identity provider insists on its own vocabulary.
 *
 * <p>Layer map (ADR 0005 §D1/§D8): {@code tenant.*} is platform-scoped, {@code topology.*} /
 * {@code directory.*} / {@code audit.business} / {@code case.read|adopt} are tenant-scoped,
 * everything else is the data plane.
 */
@Component("perms")
public class DccPermissions {

    private final DccSecurityProperties properties;

    public DccPermissions(DccSecurityProperties properties) {
        this.properties = properties;
        validateBreakGlass(properties);
    }

    /**
     * Fails fast on a misspelled break-glass permission: an emergency channel that silently grants
     * nothing is worse than none, because the operator only finds out during the incident.
     */
    private static void validateBreakGlass(DccSecurityProperties properties) {
        DccSecurityProperties.BreakGlass breakGlass = properties.getBreakGlass();
        if (breakGlass == null || breakGlass.getPermissions() == null || breakGlass.getPermissions().isEmpty()) {
            return;
        }
        Set<String> known = new LinkedHashSet<>(DccSecurityProperties.permissionNames());
        known.addAll(properties.getPermissions().keySet());
        java.util.List<String> unknown = breakGlass.getPermissions().stream()
                .filter(permission -> !known.contains(permission))
                .toList();
        if (!unknown.isEmpty()) {
            throw new IllegalStateException("dcc.security.break-glass.permissions references unknown permission "
                    + "points " + unknown + "; known permission points are " + known);
        }
        if (breakGlass.getSubjects() != null && !breakGlass.getSubjects().isEmpty()) {
            org.slf4j.LoggerFactory.getLogger(DccPermissions.class).warn(
                    "break-glass is ENABLED for subjects {} with permissions {}; disable it outside incident "
                            + "response", breakGlass.getSubjects(), breakGlass.getPermissions());
        }
    }

    // ------------------------------------------------------------------ data plane

    /** Read artifacts, history, diffs, references and exports. */
    public String getArtifactRead() {
        return permission("artifact.read");
    }

    /** Create drafts, edit, import and roll back to a previous version. */
    public String getArtifactWrite() {
        return permission("artifact.write");
    }

    /** Logically delete an artifact (retention, not removal). */
    public String getArtifactDelete() {
        return permission("artifact.delete");
    }

    /** Restore a logically deleted artifact from retention. */
    public String getArtifactRestore() {
        return permission("artifact.restore");
    }

    /** Lifecycle transitions: publish / send back (the four-eyes gate). */
    public String getArtifactTransition() {
        return permission("artifact.transition");
    }

    /** Create a migration order and submit it for approval. */
    public String getMigrationWrite() {
        return permission("migration.write");
    }

    /** Approve / reject a migration order. */
    public String getMigrationApprove() {
        return permission("migration.approve");
    }

    /** Execute / roll back an already-approved migration. */
    public String getMigrationExecute() {
        return permission("migration.execute");
    }

    /** Generate read-only projections: validation code, DTOs, OpenAPI. */
    public String getGenerationRead() {
        return permission("generation.read");
    }

    /** Package deliverables: JAR and Flyway SQL. */
    public String getGenerationPackage() {
        return permission("generation.package");
    }

    // ------------------------------------------------------------------ tenant plane

    /** List/read the tenant's environments and applications. */
    public String getTopologyRead() {
        return permission("topology.read");
    }

    /** Create/update/delete the tenant's environments and applications. */
    public String getTopologyWrite() {
        return permission("topology.write");
    }

    /** Read memberships and role assignments. */
    public String getDirectoryRead() {
        return permission("directory.read");
    }

    /** Grant/revoke memberships and roles inside the caller's own tenant. */
    public String getDirectoryWrite() {
        return permission("directory.write");
    }

    /** Grant the first tenant administrator across tenants (platform initialization). */
    public String getDirectoryWritePlatform() {
        return permission("directory.write.platform");
    }

    /** Read the tenant's business audit trail. */
    public String getAuditBusiness() {
        return permission("audit.business");
    }

    // ------------------------------------------------------------------ platform plane

    /** Read the platform audit trail (tenant admission, grants, case changes). */
    public String getAuditPlatform() {
        return permission("audit.platform");
    }

    /** Read the reference-case library. */
    public String getCaseRead() {
        return permission("case.read");
    }

    /** Adopt a reference case into the caller's tenant. */
    public String getCaseAdopt() {
        return permission("case.adopt");
    }

    /** Maintain the reference-case library (platform-authored guidance). */
    public String getCaseWrite() {
        return permission("case.write");
    }

    /** Admit tenants: create, enable/disable, retire, enumerate. */
    public String getTenantAdmit() {
        return permission("tenant.admit");
    }

    // ------------------------------------------------------------------ mapping

    /**
     * Resolves a permission name to its effective authority string. Used by
     * {@link ConfigRolePermissionResolver} to expand role bundles.
     *
     * @param name the permission name (e.g. {@code artifact.read})
     * @return the configured authority, or the name itself when unconfigured
     */
    public String resolve(String name) {
        return permission(name);
    }

    /**
     * @return every permission name: the declared catalog plus any configured remap key, used to
     *         validate role bundles so a typo fails at startup instead of silently granting nothing
     */
    public Set<String> names() {
        Set<String> all = new LinkedHashSet<>(DccSecurityProperties.permissionNames());
        all.addAll(properties.getPermissions().keySet());
        return all;
    }

    private String permission(String name) {
        String mapped = properties.getPermissions().get(name);
        return mapped == null || mapped.isBlank() ? name : mapped;
    }
}
