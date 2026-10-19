package cn.org.openbanking.dcc.web.common;

import cn.org.openbanking.dcc.core.common.error.ValidationException;
import cn.org.openbanking.dcc.core.tenant.ScopeType;
import cn.org.openbanking.dcc.security.DccSecurityProperties;

import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.stereotype.Component;

/**
 * What may be granted, and with what scope (ADR 0005 §D5).
 *
 * <p>Two rules, enforced for both directory surfaces:
 * <ul>
 *   <li><b>Tenant side</b> — only {@code dcc.security.tenant-grantable-roles} may be handed out,
 *       so a platform role cannot be laundered through a tenant-scoped grant;</li>
 *   <li><b>Both sides</b> — a role in {@code dcc.security.scope-required-roles} must name one
 *       environment or application. Granting such a role tenant-wide would quietly hand out the
 *       whole data plane instead of one surface, which is the opposite of what the role means.</li>
 * </ul>
 */
@Component
public class GrantPolicy {

    private final DccSecurityProperties properties;

    public GrantPolicy(DccSecurityProperties properties) {
        this.properties = properties;
    }

    /** A tenant administrator granting inside its own tenant: allowlist + scope rule. */
    public void requireTenantGrant(String role, ScopeType scopeType) {
        if (!properties.getTenantGrantableRoles().contains(role)) {
            throw new ValidationException("role '" + role + "' cannot be granted inside a tenant; "
                    + "grantable roles are " + properties.getTenantGrantableRoles());
        }
        requireScope(role, scopeType);
    }

    /** The platform administrator's bootstrap path: no allowlist, but the scope rule still applies. */
    public void requirePlatformGrant(String role, ScopeType scopeType) {
        requireKnownRole(role);
        requireScope(role, scopeType);
    }

    /**
     * Refuses role names the deployment never defined.
     *
     * <p>An unknown name would confer nothing, so allowing it would create a grant that looks valid
     * but silently does nothing. In {@code database} resolver mode the table is authoritative, so the
     * config-bundle check is skipped there.
     */
    private void requireKnownRole(String role) {
        if ("database".equalsIgnoreCase(properties.getResolverMode())) {
            return;
        }
        Set<String> known = new LinkedHashSet<>(properties.effectiveRoleBundles().keySet());
        if (!known.contains(role)) {
            throw new ValidationException("role '" + role + "' is not defined by dcc.security.role-bundles; "
                    + "known roles are " + known);
        }
    }

    private void requireScope(String role, ScopeType scopeType) {
        if (!properties.getScopeRequiredRoles().contains(role)) {
            return;
        }
        ScopeType effective = scopeType == null ? ScopeType.TENANT : scopeType;
        if (effective == ScopeType.TENANT) {
            throw new ValidationException("role '" + role + "' must be granted for one environment or "
                    + "application, not tenant-wide");
        }
    }
}
