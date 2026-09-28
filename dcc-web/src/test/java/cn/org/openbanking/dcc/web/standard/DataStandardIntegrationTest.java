package cn.org.openbanking.dcc.web.standard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import cn.org.openbanking.dcc.AbstractIntegrationTest;
import cn.org.openbanking.dcc.application.standard.DataStandardService;
import cn.org.openbanking.dcc.application.standard.DataStandardService.CreateCommand;
import cn.org.openbanking.dcc.application.standard.DataStandardService.UpdateCommand;
import cn.org.openbanking.dcc.application.tenant.TenantApplicationService;
import cn.org.openbanking.dcc.application.tenant.TenantEnvironmentService;
import cn.org.openbanking.dcc.application.tenant.TenantService;
import cn.org.openbanking.dcc.core.common.error.ConflictException;
import cn.org.openbanking.dcc.core.common.error.ResourceNotFoundException;
import cn.org.openbanking.dcc.core.standard.StandardContent;
import cn.org.openbanking.dcc.core.standard.StandardStatus;
import cn.org.openbanking.dcc.core.standard.diff.FieldChange;
import cn.org.openbanking.dcc.core.standard.version.VersionChangeType;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** End-to-end tests for data standards against a real PostgreSQL container. */
class DataStandardIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TenantService tenantService;
    @Autowired
    private TenantEnvironmentService environmentService;
    @Autowired
    private TenantApplicationService applicationService;
    @Autowired
    private DataStandardService standardService;

    private record Scope(String tenant, Long environmentId, Long applicationId) {
    }

    private Scope newScope() {
        String tenant = "t-" + UUID.randomUUID();
        tenantService.create(new TenantService.CreateCommand(tenant, "Tenant " + tenant, null));
        var environment = environmentService.create(tenant,
                new TenantEnvironmentService.CreateCommand("DEV", "Development", null, 1));
        var application = applicationService.create(tenant,
                new TenantApplicationService.CreateCommand("core", "Core", null));
        return new Scope(tenant, environment.id(), application.id());
    }

    @Test
    void versioningTracksHistoryDiffAndRollback() {
        Scope scope = newScope();
        StandardContent v1 = content("账号", "account number", 32);

        var created = standardService.create(scope.tenant(), scope.environmentId(), scope.applicationId(),
                new CreateCommand("acctno", "account", v1));
        assertThat(created.currentVersion()).isEqualTo("1.0.0");
        assertThat(created.status()).isEqualTo(StandardStatus.DRAFT);

        // patch: description only
        var patched = standardService.update(scope.tenant(), created.id(),
                new UpdateCommand(null, content("账号", "account number (updated)", 32)));
        assertThat(patched.currentVersion()).isEqualTo("1.0.1");

        // major: length widened (breaking)
        var major = standardService.update(scope.tenant(), created.id(),
                new UpdateCommand(null, content("账号", "account number (updated)", 64)));
        assertThat(major.currentVersion()).isEqualTo("2.0.0");

        assertThat(standardService.history(scope.tenant(), created.id())).hasSize(3);

        var diff = standardService.diff(scope.tenant(), created.id(), "1.0.0", "2.0.0");
        assertThat(diff.changeType()).isEqualTo(VersionChangeType.MAJOR);
        assertThat(diff.changes()).extracting(FieldChange::field).contains("length", "description");

        var rolled = standardService.rollback(scope.tenant(), created.id(), "1.0.0");
        assertThat(rolled.currentVersion()).isEqualTo("3.0.0");
        assertThat(rolled.content().length()).isEqualTo(32);
        assertThat(standardService.history(scope.tenant(), created.id())).hasSize(4);
    }

    @Test
    void lifecycleFollowsTheStateMachine() {
        Scope scope = newScope();
        var created = standardService.create(scope.tenant(), scope.environmentId(), scope.applicationId(),
                new CreateCommand("status", null, content("状态")));

        assertThat(standardService.transition(scope.tenant(), created.id(), StandardStatus.PENDING_REVIEW).status())
                .isEqualTo(StandardStatus.PENDING_REVIEW);
        assertThat(standardService.transition(scope.tenant(), created.id(), StandardStatus.PUBLISHED).status())
                .isEqualTo(StandardStatus.PUBLISHED);
        assertThat(standardService.transition(scope.tenant(), created.id(), StandardStatus.DEPRECATED).status())
                .isEqualTo(StandardStatus.DEPRECATED);

        // PUBLISHED can never go back to DRAFT
        assertThatThrownBy(() -> standardService.transition(scope.tenant(), created.id(), StandardStatus.DRAFT))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void duplicateCodeInSameScopeIsRejected() {
        Scope scope = newScope();
        standardService.create(scope.tenant(), scope.environmentId(), scope.applicationId(),
                new CreateCommand("dup", null, content("dup")));

        assertThatThrownBy(() -> standardService.create(scope.tenant(), scope.environmentId(),
                scope.applicationId(), new CreateCommand("dup", null, content("dup"))))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void tenantIsolationHidesOtherTenantsData() {
        Scope a = newScope();
        Scope b = newScope();
        var standard = standardService.create(a.tenant(), a.environmentId(), a.applicationId(),
                new CreateCommand("acctno", null, content("账号")));

        assertThat(standardService.get(a.tenant(), standard.id()).code()).isEqualTo("acctno");
        assertThatThrownBy(() -> standardService.get(b.tenant(), standard.id()))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(standardService.search(b.tenant(), null, null, null, null)).isEmpty();
        // B sees only its own environment, never A's
        assertThat(environmentService.list(b.tenant()))
                .extracting(TenantEnvironmentService.EnvironmentView::id)
                .containsExactly(b.environmentId());
    }

    private static StandardContent content(String name) {
        return content(name, null, 16);
    }

    private static StandardContent content(String name, String description, Integer length) {
        return new StandardContent(name, description, "VARCHAR", length, null, true, null, null, null, "123456");
    }
}
