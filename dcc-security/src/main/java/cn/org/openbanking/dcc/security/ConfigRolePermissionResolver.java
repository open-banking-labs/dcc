package cn.org.openbanking.dcc.security;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Configuration-driven {@link RolePermissionResolver}.
 *
 * <p>A role contributes, in order:
 * <ol>
 *   <li>the authorities listed under {@code dcc.security.role-authorities} (verbatim), and</li>
 *   <li>the entries of its {@code dcc.security.role-bundles} bundle, where an entry is either
 *       <em>another role</em> (expanded recursively, so an identity-provider group can simply name
 *       a role) or a <em>permission point</em> (mapped through {@code dcc.security.permissions}), and</li>
 *   <li>its own name as an authority when {@code dcc.security.roles-passthrough} is on and the role
 *       is neither mapped nor bundled (the historical behaviour).</li>
 * </ol>
 *
 * <p>Bundles only ever <em>add</em>: the token claim and the authorization directory remain the two
 * sources of roles, and this class decides what a role means.
 */
@Component
@ConditionalOnProperty(name = "dcc.security.resolver-mode", havingValue = "config", matchIfMissing = true)
public class ConfigRolePermissionResolver implements RolePermissionResolver {

    private static final Logger log = LoggerFactory.getLogger(ConfigRolePermissionResolver.class);

    private final DccSecurityProperties properties;
    private final DccPermissions permissions;

    /** Convenience form for embedders that resolve permission names verbatim. */
    public ConfigRolePermissionResolver(DccSecurityProperties properties) {
        this(properties, new DccPermissions(properties));
    }

    @Autowired
    public ConfigRolePermissionResolver(DccSecurityProperties properties, DccPermissions permissions) {
        this.properties = properties;
        this.permissions = permissions;
        validateBundles(properties.effectiveRoleBundles(), permissions.names());
        if (properties.isRolesPassthrough()) {
            log.warn("dcc.security.roles-passthrough is on: any JWT role becomes its own authority. "
                    + "Keep it for local development only; production deployments should set it to "
                    + "false and map identity-provider groups through dcc.security.role-bundles.");
        }
    }

    @Override
    public Set<String> authoritiesFor(Collection<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return Set.of();
        }
        Set<String> authorities = new LinkedHashSet<>();
        for (String role : roles) {
            authorities.addAll(expand(role, new LinkedHashSet<>()));
        }
        return authorities;
    }

    /**
     * Fails fast when a bundle entry names neither a role nor a declared permission point: a typo
     * would otherwise silently grant nothing, i.e. the role would look configured but confer no power.
     */
    private static void validateBundles(Map<String, List<String>> bundles, Set<String> knownPermissions) {
        if (bundles == null || bundles.isEmpty()) {
            return;
        }
        List<String> problems = new ArrayList<>();
        bundles.forEach((role, entries) -> {
            if (entries == null) {
                return;
            }
            for (String entry : entries) {
                if (!bundles.containsKey(entry) && !knownPermissions.contains(entry)) {
                    problems.add(role + " -> " + entry);
                }
            }
        });
        if (!problems.isEmpty()) {
            throw new IllegalStateException("dcc.security.role-bundles references unknown entries (neither a role "
                    + "nor a permission point): " + problems + "; known permission points: " + knownPermissions);
        }
    }

    /**
     * Expands one role into authorities.
     *
     * @param role    the role to expand
     * @param visiting roles currently being expanded, so a misconfigured bundle cycle terminates
     *                instead of looping
     */
    private Set<String> expand(String role, Set<String> visiting) {
        Map<String, List<String>> mappings = properties.getRoleAuthorities();
        Map<String, List<String>> bundles = properties.effectiveRoleBundles();
        List<String> mapped = mappings == null ? null : mappings.get(role);
        List<String> bundle = bundles == null ? null : bundles.get(role);

        Set<String> authorities = new LinkedHashSet<>();
        if (mapped != null) {
            authorities.addAll(mapped);
        }
        if (bundle == null) {
            if (mapped == null && properties.isRolesPassthrough()) {
                authorities.add(role);
            }
            return authorities;
        }
        if (!visiting.add(role)) {
            // bundle cycle: keep what this role already contributed, do not recurse further
            log.warn("dcc.security.role-bundles contains a cycle at role '{}'; expansion stopped", role);
            return authorities;
        }
        try {
            for (String entry : bundle) {
                if (bundles != null && bundles.containsKey(entry)) {
                    // a bundle entry may name another role (IdP group -> role composition)
                    authorities.addAll(expand(entry, visiting));
                } else {
                    // otherwise it is a permission point (or an authority literal)
                    authorities.add(permissions.resolve(entry));
                }
            }
        } finally {
            visiting.remove(role);
        }
        return authorities;
    }
}
