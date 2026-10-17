package cn.org.openbanking.dcc.security;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

/**
 * Configuration-driven {@link RolePermissionResolver}: a role listed under
 * {@code dcc.security.role-authorities} is expanded to the authorities there; a role
 * not listed is used verbatim as its own authority when
 * {@code dcc.security.roles-passthrough} is on (default, the historical behaviour),
 * and confers nothing when it is off.
 */
@Component
public class ConfigRolePermissionResolver implements RolePermissionResolver {

    private final DccSecurityProperties properties;

    public ConfigRolePermissionResolver(DccSecurityProperties properties) {
        this.properties = properties;
    }

    @Override
    public Set<String> authoritiesFor(Collection<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return Set.of();
        }
        Map<String, List<String>> mapping = properties.getRoleAuthorities();
        Set<String> authorities = new LinkedHashSet<>();
        for (String role : roles) {
            List<String> mapped = mapping == null ? null : mapping.get(role);
            if (mapped != null) {
                authorities.addAll(mapped);
            } else if (properties.isRolesPassthrough()) {
                authorities.add(role);
            }
        }
        return authorities;
    }
}
