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
 * not listed is used verbatim as its own authority. With the default empty map this
 * is a no-op (role == authority), preserving the previous behaviour.
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
            } else {
                authorities.add(role);
            }
        }
        return authorities;
    }
}
