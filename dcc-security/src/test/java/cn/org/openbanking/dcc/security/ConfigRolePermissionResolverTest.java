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
    void unknownRolesConferNothing() {
        assertThat(resolver().authoritiesFor(List.of("ROLE_EDITOR", "ROLE_OTHER"))).isEmpty();
    }

    @Test
    void legacyRoleNamesConferNothing() {
        assertThat(resolver().authoritiesFor(List.of("DCC_ADMIN"))).isEmpty();
        assertThat(resolver().authoritiesFor(List.of("DCC_USER"))).isEmpty();
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
                "directory.write", "audit.business", "announcement.write");
        assertThat(authorities).doesNotContain("artifact.transition", "migration.approve",
                "tenant.admit", "case.write", "directory.write.platform",
                // a tenant administrator may address its own tenant only, never every tenant
                "announcement.write.platform");
    }

    @Test
    void platformAdminCannotReadAnyTenantBusinessData() {
        Set<String> authorities = resolver().authoritiesFor(List.of("DCC_PLATFORM_ADMIN"));

        assertThat(authorities).containsExactlyInAnyOrder("tenant.admit", "directory.read",
                "directory.write.platform", "audit.platform", "case.write", "case.read",
                // platform-wide announcements are a platform plane, like the platform audit trail
                "announcement.write.platform");
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
    void permissionNamesAreRemappable() {
        DccSecurityProperties properties = new DccSecurityProperties();
        properties.getPermissions().put("artifact.write", "DCC_ARTIFACT_WRITE");

        Set<String> authorities = resolver(properties).authoritiesFor(List.of("DCC_MAINTAINER"));

        assertThat(authorities).contains("DCC_ARTIFACT_WRITE").doesNotContain("artifact.write");
    }

    @Test
    void bundleCyclesTerminateInsteadOfLooping() {
        DccSecurityProperties properties = new DccSecurityProperties();
        properties.getRoleBundles().put("GROUP_A", List.of("GROUP_B", "artifact.read"));
        properties.getRoleBundles().put("GROUP_B", List.of("GROUP_A"));

        assertThat(resolver(properties).authoritiesFor(List.of("GROUP_A")))
                .containsExactlyInAnyOrder("artifact.read");
    }

    @Test
    void bundlesAreConfigureOrTakeDefaults() {
        // nothing configured -> exactly the shipped six-role model
        assertThat(new DccSecurityProperties().effectiveRoleBundles()).containsOnlyKeys(
                "DCC_READER", "DCC_MAINTAINER", "DCC_APPROVER", "DCC_ENV_ADMIN",
                "DCC_TENANT_ADMIN", "DCC_PLATFORM_ADMIN");

        // anything configured replaces the defaults wholesale
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
        properties.getRoleBundles().put("DCC_APPROVER",
                List.of("artifact.transition", "migration.approve", "artifact.read"));
        properties.getRoleBundles().put("DCC_APPROVERS", List.of("DCC_APPROVER"));

        Set<String> authorities = resolver(properties).authoritiesFor(List.of("DCC_APPROVERS"));

        assertThat(authorities).contains("migration.approve", "artifact.transition");
    }
}
