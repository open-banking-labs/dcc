package cn.org.openbanking.dcc.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class ConfigRolePermissionResolverTest {

    @Test
    void rolesAreUsedVerbatimWhenNothingIsMapped() {
        RolePermissionResolver resolver = new ConfigRolePermissionResolver(new DccSecurityProperties());

        assertThat(resolver.authoritiesFor(List.of("DCC_ADMIN", "ROLE_EDITOR")))
                .containsExactlyInAnyOrder("DCC_ADMIN", "ROLE_EDITOR");
    }

    @Test
    void mappedRolesExpandAndUnmappedRolesAreKept() {
        DccSecurityProperties properties = new DccSecurityProperties();
        properties.getRoleAuthorities().put("editor", List.of("DCC_USER", "DCC_APPROVER"));

        RolePermissionResolver resolver = new ConfigRolePermissionResolver(properties);

        assertThat(resolver.authoritiesFor(List.of("editor", "other")))
                .containsExactlyInAnyOrder("DCC_USER", "DCC_APPROVER", "other");
    }

    @Test
    void emptyOrNullRolesYieldNoAuthorities() {
        RolePermissionResolver resolver = new ConfigRolePermissionResolver(new DccSecurityProperties());

        assertThat(resolver.authoritiesFor(List.of())).isEmpty();
        assertThat(resolver.authoritiesFor(null)).isEmpty();
    }
}
