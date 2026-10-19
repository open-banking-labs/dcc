package cn.org.openbanking.dcc.application.authorization;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import cn.org.openbanking.dcc.core.authorization.RolePermission;
import cn.org.openbanking.dcc.core.authorization.repository.RolePermissionRepository;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads role → permission mappings from the database (ADR 0005 §D8).
 *
 * <p>This is the application-layer half of the pluggable resolver: {@code dcc-security} owns the
 * {@code RolePermissionResolver} interface but must stay free of JPA, exactly like
 * {@code TenantDirectoryPolicy} vs {@code DirectoryAccessPolicy}. The exposure layers bridge the two
 * (see {@code DatabaseRolePermissionConfiguration} in dcc-web / dcc-mcp).
 *
 * <p>One indexed query per request; the table is tiny and a deployment that turns this on has
 * explicitly accepted a database read on the security path (it already performs several in
 * directory mode).
 */
@Component
@Transactional(readOnly = true)
public class RolePermissionCatalog {

    private final RolePermissionRepository mappings;

    public RolePermissionCatalog(RolePermissionRepository mappings) {
        this.mappings = mappings;
    }

    /** @return the permission names granted by these roles (never null). */
    public Set<String> permissionsFor(Collection<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return Set.of();
        }
        List<RolePermission> found = mappings.findByRoleIn(roles);
        if (found.isEmpty()) {
            return Set.of();
        }
        Set<String> permissions = new LinkedHashSet<>();
        for (RolePermission mapping : found) {
            permissions.add(mapping.getPermission());
        }
        return permissions;
    }

    /** @return true when the role has at least one live mapping (used by the startup diagnostics). */
    public boolean isConfigured(String role) {
        return !mappings.findByRoleIn(List.of(role)).isEmpty();
    }
}
