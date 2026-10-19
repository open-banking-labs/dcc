package cn.org.openbanking.dcc.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class ConfigRolePermissionResolverTest {

    private static RolePermissionResolver resolver(DccSecurityProperties properties) {
        return new ConfigRolePermissionResolver(properties, new DccPermissions(properties));
    }

    private static RolePermissionResolver resolver() {
        return resolver(new DccSecurityProperties());
    }

    @Test
    void rolesAreUsedVerbatimWhenNothingIsMapped() {
        // ROLE_EDITOR / ROLE_OTHER carry neither a mapping nor a bundle, so passthrough applies
        assertThat(resolver().authoritiesFor(List.of("ROLE_EDITOR", "ROLE_OTHER")))
                .containsExactlyInAnyOrder("ROLE_EDITOR", "ROLE_OTHER");
    }

    @Test
    void mappedRolesExpandAndUnmappedRolesAreKept() {
        DccSecurityProperties properties = new DccSecurityProperties();
        properties.getRoleAuthorities().put("editor", List.of("DCC_USER", "DCC_APPROVER"));

        assertThat(resolver(properties).authoritiesFor(List.of("editor", "ROLE_OTHER")))
                .containsExactlyInAnyOrder("DCC_USER", "DCC_APPROVER", "ROLE_OTHER");
    }

    @Test
    void unmappedRolesConferNothingWhenPassthroughIsOff() {
        DccSecurityProperties properties = new DccSecurityProperties();
        properties.setRolesPassthrough(false);
        properties.getRoleAuthorities().put("editor", List.of("DCC_USER", "DCC_APPROVER"));

        // a role that merely looks like an authority no longer grants it
        assertThat(resolver(properties).authoritiesFor(List.of("editor", "ROLE_OTHER", "ROLE_DCC_ADMIN")))
                .containsExactlyInAnyOrder("DCC_USER", "DCC_APPROVER");
    }

    @Test
    void strictModeStillHonoursDirectlyMappedAuthorities() {
        DccSecurityProperties properties = new DccSecurityProperties();
        properties.setRolesPassthrough(false);
        properties.getRoleAuthorities().put("ops", List.of("DCC_ADMIN"));

        assertThat(resolver(properties).authoritiesFor(List.of("ops"))).containsExactly("DCC_ADMIN");
    }

    @Test
    void emptyOrNullRolesYieldNoAuthorities() {
        assertThat(resolver().authoritiesFor(List.of())).isEmpty();
        assertThat(resolver().authoritiesFor(null)).isEmpty();
    }

    // ------------------------------------------------------------------ bundles (ADR 0005)

    @Test
    void approverBundleGatekeepsButCannotWriteOrExecute() {
        Set<String> authorities = resolver().authoritiesFor(List.of("DCC_APPROVER"));

        assertThat(authorities).contains("artifact.transition", "migration.approve", "artifact.read");
        assertThat(authorities).doesNotContain("artifact.write", "migration.execute", "tenant.admit");
    }

    @Test
    void maintainerBundleWritesButCannotApprove() {
        Set<String> authorities = resolver().authoritiesFor(List.of("DCC_MAINTAINER"));

        assertThat(authorities).contains("artifact.write", "artifact.delete", "migration.write");
        assertThat(authorities).doesNotContain("artifact.transition", "migration.approve",
                "migration.execute", "topology.write");
    }

    @Test
    void tenantAdminExecutesAndGovernsItsTenantButCannotApproveOrAdmitTenants() {
        Set<String> authorities = resolver().authoritiesFor(List.of("DCC_TENANT_ADMIN"));

        assertThat(authorities).contains("migration.execute", "artifact.restore", "topology.write",
                "directory.write", "audit.business");
        assertThat(authorities).doesNotContain("artifact.transition", "migration.approve",
                "tenant.admit", "case.write", "directory.write.platform");
    }

    @Test
    void platformAdminCannotReadAnyTenantBusinessData() {
        Set<String> authorities = resolver().authoritiesFor(List.of("DCC_PLATFORM_ADMIN"));

        assertThat(authorities).contains("tenant.admit", "case.write", "audit.platform",
                "directory.write.platform");
        assertThat(authorities).doesNotContain("artifact.read", "artifact.write", "topology.read",
                "audit.business", "directory.write");
    }

    @Test
    void environmentAdministratorKeepsTheDataPlaneButNotApprovalOrTopology() {
        Set<String> authorities = resolver().authoritiesFor(List.of("DCC_ENV_ADMIN"));

        assertThat(authorities).contains("artifact.read", "artifact.write", "artifact.delete",
                "migration.write", "migration.execute", "generation.package");
        // approval stays with DCC_APPROVER; environments/roles/audit stay tenant-administrator only
        assertThat(authorities).doesNotContain("artifact.transition", "migration.approve",
                "topology.write", "directory.write", "audit.business", "tenant.admit");
    }

    @Test
    void readerBundleIsReadOnly() {
        Set<String> authorities = resolver().authoritiesFor(List.of("DCC_READER"));

        assertThat(authorities).containsExactlyInAnyOrder("artifact.read", "generation.read",
                "topology.read");
    }

    @Test
    void legacyAdminKeepsEverythingAndLegacyUserKeepsWriteButNotAdmission() {
        Set<String> legacyAdmin = resolver().authoritiesFor(List.of("DCC_ADMIN"));
        assertThat(legacyAdmin).contains("artifact.write", "migration.execute", "tenant.admit",
                "directory.write", "case.write");

        Set<String> legacyUser = resolver().authoritiesFor(List.of("DCC_USER"));
        assertThat(legacyUser).contains("artifact.read", "artifact.write", "migration.write");
        assertThat(legacyUser).doesNotContain("tenant.admit", "directory.write", "audit.business",
                "migration.approve");
    }

    @Test
    void permissionNamesAreRemappable() {
        DccSecurityProperties properties = new DccSecurityProperties();
        properties.getPermissions().put("artifact.write", "DCC_ARTIFACT_WRITE");

        Set<String> authorities = resolver(properties).authoritiesFor(List.of("DCC_MAINTAINER"));

        assertThat(authorities).contains("DCC_ARTIFACT_WRITE").doesNotContain("artifact.write");
    }

    @Test
    void bundleCyclesTerminateInsteadOfLooping() {
        DccSecurityProperties properties = new DccSecurityProperties();
        properties.setRolesPassthrough(false);
        properties.getRoleBundles().put("GROUP_A", List.of("GROUP_B", "artifact.read"));
        properties.getRoleBundles().put("GROUP_B", List.of("GROUP_A"));

        assertThat(resolver(properties).authoritiesFor(List.of("GROUP_A")))
                .containsExactlyInAnyOrder("artifact.read");
    }

    @Test
    void bundlesAreConfigureOrTakeDefaults() {
        // nothing configured -> the shipped five-role model plus the legacy hats
        assertThat(new DccSecurityProperties().effectiveRoleBundles())
                .containsKeys("DCC_READER", "DCC_MAINTAINER", "DCC_APPROVER", "DCC_TENANT_ADMIN",
                        "DCC_PLATFORM_ADMIN", "DCC_ADMIN", "DCC_USER");

        // anything configured replaces the defaults wholesale, so a deployment can drop the legacy hats
        DccSecurityProperties configured = new DccSecurityProperties();
        configured.getRoleBundles().put("MY_ROLE", List.of("artifact.read"));
        assertThat(configured.effectiveRoleBundles()).containsOnlyKeys("MY_ROLE");
    }

    @Test
    void unknownBundleEntriesFailFast() {
        DccSecurityProperties properties = new DccSecurityProperties();
        properties.getRoleBundles().put("TYPO_ROLE", List.of("migration.aprove"));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> resolver(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("migration.aprove");
    }

    @Test
    void identityProviderGroupsMapOntoRoles() {
        DccSecurityProperties properties = new DccSecurityProperties();
        properties.setRolesPassthrough(false);
        // configuring bundles replaces the shipped defaults, so the composed role must be defined too
        properties.getRoleBundles().put("DCC_APPROVER",
                List.of("artifact.transition", "migration.approve", "artifact.read"));
        properties.getRoleBundles().put("DCC_APPROVERS", List.of("DCC_APPROVER"));

        Set<String> authorities = resolver(properties).authoritiesFor(List.of("DCC_APPROVERS"));

        assertThat(authorities).contains("migration.approve", "artifact.transition");
    }
}
