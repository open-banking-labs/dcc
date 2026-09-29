package cn.org.openbanking.dcc.security;

import java.util.Collection;
import java.util.Set;

/**
 * Resolves the authorities a caller holds from the roles carried by its token.
 *
 * <p>This is the seam for the permission model: the built-in
 * {@link ConfigRolePermissionResolver} reads {@code dcc.security.role-authorities},
 * and a database-backed (role / permission / role_permission) implementation can be
 * supplied instead without touching the filter.
 */
public interface RolePermissionResolver {

    /** @return the authorities granted by the given roles (never {@code null}). */
    Set<String> authoritiesFor(Collection<String> roles);
}
