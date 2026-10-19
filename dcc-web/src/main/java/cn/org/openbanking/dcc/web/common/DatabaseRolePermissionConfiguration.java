package cn.org.openbanking.dcc.web.common;

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
 * Bridges the application-owned role → permission catalog onto the security module's
 * {@link RolePermissionResolver} seam, active only when {@code dcc.security.resolver-mode=database}
 * (ADR 0005 §D8).
 *
 * <p>The bridge lives in the exposure layer for the same reason
 * {@code DirectoryAccessPolicyConfiguration} does: {@code dcc-security} must not depend on JPA, and
 * {@code dcc-application} must not depend on security types.
 *
 * <p>In this mode the table is authoritative: a role with no rows confers nothing (no passthrough),
 * which is the "closed vocabulary" behaviour an enterprise deployment wants.
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
