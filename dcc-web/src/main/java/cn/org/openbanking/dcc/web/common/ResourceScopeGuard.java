package cn.org.openbanking.dcc.web.common;

import java.util.LinkedHashSet;
import java.util.Set;

import cn.org.openbanking.dcc.application.common.ArtifactResourceKind;
import cn.org.openbanking.dcc.application.common.OperatingSurface;
import cn.org.openbanking.dcc.application.common.ResourceScopeResolver;
import cn.org.openbanking.dcc.core.tenant.ScopeType;
import cn.org.openbanking.dcc.security.DccPermissions;
import cn.org.openbanking.dcc.security.DirectoryAccessPolicy;
import cn.org.openbanking.dcc.security.InternalIdentity;
import cn.org.openbanking.dcc.security.RolePermissionResolver;
import cn.org.openbanking.dcc.security.TenantContext;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Scope-aware authorization for the approval gates (ADR 0005, closing ADR 0004's known limitation).
 *
 * <p>Authority from the token and the directory is tenant-wide by default. A grant scoped to one
 * environment or application only becomes usable when the request declares that surface — which an
 * opaque-id endpoint such as {@code POST /api/migrations/{id}/approve} never does. This guard closes
 * the gap from the other side: it resolves the resource's <em>real</em> surface from the database and
 * asks the directory whether the caller holds the required permission <em>for that surface</em>.
 *
 * <p>It is the single decision point for these endpoints:
 *
 * <pre>
 * &#64;PreAuthorize("&#64;scopeGuard.mayApproveMigration(#id)")
 * </pre>
 *
 * <p>Safety properties:
 * <ul>
 *   <li>the tenant-wide path is the caller's current authorities, exactly as before — nothing that
 *       used to be allowed becomes denied;</li>
 *   <li>the surface is read from the row, never from the request, so it cannot be forged;</li>
 *   <li>a row that does not exist, or belongs to another tenant, yields "no scoped grant" — the
 *       caller then only has the tenant-wide path;</li>
 *   <li>without a {@link DirectoryAccessPolicy} (claim-only deployments) there are no scoped grants,
 *       so the guard reduces to the tenant-wide check.</li>
 * </ul>
 */
@Component("scopeGuard")
public class ResourceScopeGuard {

    private final ResourceScopeResolver scopes;
    private final ObjectProvider<DirectoryAccessPolicy> directory;
    private final RolePermissionResolver rolePermissions;
    private final DccPermissions permissions;

    public ResourceScopeGuard(ResourceScopeResolver scopes,
            ObjectProvider<DirectoryAccessPolicy> directory,
            RolePermissionResolver rolePermissions,
            DccPermissions permissions) {
        this.scopes = scopes;
        this.directory = directory;
        this.rolePermissions = rolePermissions;
        this.permissions = permissions;
    }

    /** Approve/reject a migration order: tenant-wide, or scoped to the order's target environment. */
    public boolean mayApproveMigration(Long id) {
        return grantedFor(ArtifactResourceKind.MIGRATION_ORDER, id, permissions.getMigrationApprove());
    }

    /** Execute/roll back an approved migration: tenant-wide, or scoped to the target environment. */
    public boolean mayExecuteMigration(Long id) {
        return grantedFor(ArtifactResourceKind.MIGRATION_ORDER, id, permissions.getMigrationExecute());
    }

    /** Edit / version-rollback an artifact: tenant-wide, or scoped to its environment/application. */
    public boolean mayWriteArtifact(String kind, Long id) {
        return grantedFor(kindOf(kind), id, permissions.getArtifactWrite());
    }

    /**
     * Logically delete an artifact: tenant-wide, or scoped to its environment/application.
     *
     * <p>Restore is deliberately <em>not</em> scope-aware: the row is logically deleted, so it is
     * hidden from the surface lookup, and resurrection is a retention action the tenant
     * administrator performs tenant-wide (it is audited like every other change).
     */
    public boolean mayDeleteArtifact(String kind, Long id) {
        return grantedFor(kindOf(kind), id, permissions.getArtifactDelete());
    }

    /**
     * An artifact lifecycle transition.
     *
     * <p>The permission depends on the target: submitting for review ({@code PENDING_REVIEW}) is the
     * maintainer's step ({@code artifact.write}); publish / send back / deprecate are the approver's
     * step ({@code artifact.transition}).
     *
     * @param target the requested status, as an enum or its name
     */
    public boolean mayTransition(String kind, Long id, Object target) {
        ArtifactResourceKind resourceKind = kindOf(kind);
        if (resourceKind == null) {
            return false;
        }
        String status = target == null ? "" : target.toString();
        String required = "PENDING_REVIEW".equals(status)
                ? permissions.getArtifactWrite()
                : permissions.getArtifactTransition();
        return grantedFor(resourceKind, id, required);
    }

    /** @return true when the caller holds the permission tenant-wide, or for this resource's surface */
    private boolean grantedFor(ArtifactResourceKind kind, Long id, String requiredPermission) {
        if (requiredPermission == null) {
            return false;
        }
        if (holds(requiredPermission)) {
            return true;
        }
        return scopedGrant(kind, id, requiredPermission);
    }

    private boolean scopedGrant(ArtifactResourceKind kind, Long id, String requiredPermission) {
        String tenant = TenantContext.get();
        String subject = subject();
        if (tenant == null || subject == null) {
            return false;
        }
        DirectoryAccessPolicy policy = directory.getIfAvailable();
        if (policy == null) {
            return false;
        }
        OperatingSurface surface = scopes.resolve(tenant, kind, id).orElse(null);
        if (surface == null) {
            return false;
        }
        Set<String> grantedRoles = new LinkedHashSet<>();
        if (surface.environmentId() != null && surface.environmentCode() != null) {
            grantedRoles.addAll(policy.assignedRoles(subject, tenant, ScopeType.ENVIRONMENT.name(),
                    surface.environmentCode(), surface.environmentId()));
        }
        if (surface.applicationId() != null && surface.applicationCode() != null) {
            grantedRoles.addAll(policy.assignedRoles(subject, tenant, ScopeType.APPLICATION.name(),
                    surface.applicationCode(), surface.applicationId()));
        }
        if (grantedRoles.isEmpty()) {
            return false;
        }
        return rolePermissions.authoritiesFor(grantedRoles).contains(requiredPermission);
    }

    private static boolean holds(String authority) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority::equals);
    }

    private static ArtifactResourceKind kindOf(String kind) {
        if (kind == null) {
            return null;
        }
        try {
            return ArtifactResourceKind.valueOf(kind);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static String subject() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return null;
        }
        if (authentication.getPrincipal() instanceof InternalIdentity identity) {
            return identity.subject();
        }
        return authentication.getName();
    }
}
