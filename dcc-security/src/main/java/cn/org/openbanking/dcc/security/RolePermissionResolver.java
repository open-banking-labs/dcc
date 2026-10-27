package cn.org.openbanking.dcc.security;

import java.util.Collection;
import java.util.Set;

/**
 * Resolves the authorities a caller holds from the roles carried by its token.
 *
 * <p>This is the seam for the permission model: the built-in
 * {@link ConfigRolePermissionResolver} reads {@code dcc.security.role-bundles},
 * and a database-backed (role / permission / role_permission) implementation can be
 * supplied instead without touching the filter. A role with no explicit mapping confers
 * no authority.
 */
public interface RolePermissionResolver {

    /** @return the authorities granted by the given roles (never {@code null}). */
    Set<String> authoritiesFor(Collection<String> roles);

    /**
     * Reports whether the roles grant a specific canonical role. Implementations that use
     * role aliases should follow their bundle composition so identity-provider groups and
     * canonical roles are evaluated consistently.
     */
    default boolean grantsRole(Collection<String> roles, String role) {
        return roles != null && role != null && roles.contains(role);
    }
}
