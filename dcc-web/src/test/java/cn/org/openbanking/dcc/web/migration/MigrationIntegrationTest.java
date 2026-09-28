package cn.org.openbanking.dcc.web.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import cn.org.openbanking.dcc.AbstractIntegrationTest;
import cn.org.openbanking.dcc.application.migration.MigrationService;
import cn.org.openbanking.dcc.application.migration.MigrationService.CreateCommand;
import cn.org.openbanking.dcc.application.standard.DataStandardService;
import cn.org.openbanking.dcc.application.tenant.TenantApplicationService;
import cn.org.openbanking.dcc.application.tenant.TenantEnvironmentService;
import cn.org.openbanking.dcc.application.tenant.TenantService;
import cn.org.openbanking.dcc.core.common.error.ConflictException;
import cn.org.openbanking.dcc.core.migration.ArtifactType;
import cn.org.openbanking.dcc.core.migration.MigrationStatus;
import cn.org.openbanking.dcc.core.standard.StandardContent;
import cn.org.openbanking.dcc.core.standard.StandardStatus;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Verifies the migration/approval flow and that a target environment (PROD) can only
 * receive a version via an approved migration, never direct maintenance.
 */
class MigrationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TenantService tenantService;
    @Autowired
    private TenantEnvironmentService environmentService;
    @Autowired
    private TenantApplicationService applicationService;
    @Autowired
    private DataStandardService standardService;
    @Autowired
    private MigrationService migrationService;

    private record Scope(String tenant, Long dev, Long prod, Long applicationId) {
    }

    private Scope newScope() {
        String tenant = "t-" + UUID.randomUUID();
        tenantService.create(new TenantService.CreateCommand(tenant, "Tenant " + tenant, null));
        var dev = environmentService.create(tenant,
                new TenantEnvironmentService.CreateCommand("DEV", "Development", null, 1));
        var prod = environmentService.create(tenant,
                new TenantEnvironmentService.CreateCommand("PROD", "Production", null, 2));
        var application = applicationService.create(tenant,
                new TenantApplicationService.CreateCommand("core", "Core", null));
        return new Scope(tenant, dev.id(), prod.id(), application.id());
    }

    private static StandardContent content() {
        return new StandardContent("账号", "account", "VARCHAR", 32, null, true, null, null, null, null);
    }

    @Test
    void productionCanOnlyReceiveVersionsThroughAnApprovedMigration() {
        Scope scope = newScope();

        // a standard is maintained directly in DEV
        var devStandard = standardService.create(scope.tenant(), scope.dev(), scope.applicationId(),
                new DataStandardService.CreateCommand("acctno", "account", content()));

        // direct maintenance in PROD is rejected
        assertThatThrownBy(() -> standardService.create(scope.tenant(), scope.prod(), scope.applicationId(),
                new DataStandardService.CreateCommand("acctno", "account", content())))
                .isInstanceOf(ConflictException.class);

        // migration order: DEV -> PROD, with approval
        var order = migrationService.create(scope.tenant(), new CreateCommand(
                ArtifactType.DATA_STANDARD, devStandard.id(), scope.dev(), scope.prod(), "promote to prod"));
        assertThat(order.status()).isEqualTo(MigrationStatus.PENDING_SUBMISSION);

        // cannot execute before approval
        assertThatThrownBy(() -> migrationService.execute(scope.tenant(), order.id()))
                .isInstanceOf(ConflictException.class);

        assertThat(migrationService.submit(scope.tenant(), order.id()).status())
                .isEqualTo(MigrationStatus.PENDING_APPROVAL);
        assertThat(migrationService.approve(scope.tenant(), order.id()).status())
                .isEqualTo(MigrationStatus.APPROVED);

        var migrated = migrationService.execute(scope.tenant(), order.id());
        assertThat(migrated.status()).isEqualTo(MigrationStatus.MIGRATED);
        assertThat(migrated.targetArtifactId()).isNotNull();

        // the PROD standard now exists with the same content
        var prodStandard = standardService.get(scope.tenant(), migrated.targetArtifactId());
        assertThat(prodStandard.environmentId()).isEqualTo(scope.prod());
        assertThat(prodStandard.content()).isEqualTo(devStandard.content());

        // rollback removes the migrated PROD version
        assertThat(migrationService.rollback(scope.tenant(), order.id()).status())
                .isEqualTo(MigrationStatus.ROLLED_BACK);
        assertThatThrownBy(() -> standardService.get(scope.tenant(), migrated.targetArtifactId()))
                .isInstanceOf(cn.org.openbanking.dcc.core.common.error.ResourceNotFoundException.class);
    }

    @Test
    void rejectedOrderCannotBeExecuted() {
        Scope scope = newScope();
        var devStandard = standardService.create(scope.tenant(), scope.dev(), scope.applicationId(),
                new DataStandardService.CreateCommand("acctno", "account", content()));

        var order = migrationService.create(scope.tenant(), new CreateCommand(
                ArtifactType.DATA_STANDARD, devStandard.id(), scope.dev(), scope.prod(), null));
        migrationService.submit(scope.tenant(), order.id());
        assertThat(migrationService.reject(scope.tenant(), order.id()).status()).isEqualTo(MigrationStatus.REJECTED);

        assertThatThrownBy(() -> migrationService.execute(scope.tenant(), order.id()))
                .isInstanceOf(ConflictException.class);
    }
}
