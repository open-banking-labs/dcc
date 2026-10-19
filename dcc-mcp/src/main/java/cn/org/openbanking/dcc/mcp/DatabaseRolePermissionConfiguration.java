package cn.org.openbanking.dcc.mcp;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

import cn.org.openbanking.dcc.application.authorization.RolePermissionCatalog;
import cn.org.openbanking.dcc.security.DccPermissions;
import cn.org.openbanking.dcc.security.RolePermissionResolver;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MCP-side twin of {@code dcc-web}'s {@code DatabaseRolePermissionConfiguration}: activates the
 * database-backed role → permission resolver when {@code dcc.security.resolver-mode=database}
 * (ADR 0005 §D8). Kept in each exposure layer because the bridge needs both security types and
 * application types, and neither module may depend on the other.
 */
@Configuration
@ConditionalOnProperty(name = "dcc.security.resolver-mode", havingValue = "database")
public class DatabaseRolePermissionConfiguration {

    @Bean
    @ConditionalOnMissingBean(RolePermissionResolver.class)
    RolePermissionResolver rolePermissionResolver(RolePermissionCatalog catalog, DccPermissions permissions) {
        return new RolePermissionResolver() {
            @Override
            public Set<String> authoritiesFor(Collection<String> roles) {
                Set<String> authorities = new LinkedHashSet<>();
                for (String permission : catalog.permissionsFor(roles)) {
                    authorities.add(permissions.resolve(permission));
                }
                return authorities;
            }
        };
    }
}
