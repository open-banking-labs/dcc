package cn.org.openbanking.dcc.web.versioning;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import cn.org.openbanking.dcc.AbstractIntegrationTest;
import cn.org.openbanking.dcc.application.common.ChangeContext;
import cn.org.openbanking.dcc.application.standard.DataStandardService;
import cn.org.openbanking.dcc.application.standard.DataStandardService.UpdateCommand;
import cn.org.openbanking.dcc.application.standard.DataStandardService.VersionView;
import cn.org.openbanking.dcc.application.tenant.TenantApplicationService;
import cn.org.openbanking.dcc.application.tenant.TenantEnvironmentService;
import cn.org.openbanking.dcc.application.tenant.TenantService;
import cn.org.openbanking.dcc.core.standard.StandardContent;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Verifies the three-axis versioning model end to end:
 * <ul>
 *   <li><b>content hash</b> - identity &amp; integrity: identical content is a no-op
 *       (no new version), and the stored hash verifies;</li>
 *   <li><b>semantic version</b> - the compatibility promise for a hash transition;</li>
 *   <li><b>lineage</b> - {@code parentHash} links versions, with author/message
 *       recorded (Git-like history).</li>
 * </ul>
 */
class ThreeAxisVersioningIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TenantService tenantService;
    @Autowired
    private TenantEnvironmentService environmentService;
    @Autowired
    private TenantApplicationService applicationService;
    @Autowired
    private DataStandardService standardService;
    @Autowired
    private ChangeContext changeContext;

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

    private static StandardContent content(Integer length) {
        return new StandardContent("账号", "account", "VARCHAR", length, null, true, null, null, null, null);
    }

    @Test
    void contentHashIsIdentitySemverIsPromiseLineageIsGitLike() {
        Scope scope = newScope();
        var created = standardService.create(scope.tenant(), scope.environmentId(), scope.applicationId(),
                new DataStandardService.CreateCommand("acctno", "account", content(32)));

        // --- axis 1: content hash (identity + integrity) ---
        List<VersionView> first = standardService.history(scope.tenant(), created.id());
        assertThat(first).hasSize(1);
        String hashV1 = first.get(0).contentHash();
        assertThat(hashV1).startsWith("sha256:");
        assertThat(first.get(0).parentHash()).isNull();
        assertThat(first.get(0).author()).isEqualTo("system");
        assertThat(standardService.verify(scope.tenant(), created.id(), "1.0.0")).isTrue();

        // identical content is a no-op: hash identity means no new version
        changeContext.set("alice", "attempt a no-op");
        try {
            var same = standardService.update(scope.tenant(), created.id(), new UpdateCommand(null, content(32)));
            assertThat(same.currentVersion()).isEqualTo("1.0.0");
            assertThat(standardService.history(scope.tenant(), created.id())).hasSize(1);
        } finally {
            changeContext.clear();
        }

        // --- axis 2 & 3: real change -> SemVer bump + Git-like lineage ---
        changeContext.set("alice", "widen the account number");
        try {
            var changed = standardService.update(scope.tenant(), created.id(), new UpdateCommand(null, content(64)));
            assertThat(changed.currentVersion()).isEqualTo("2.0.0"); // MAJOR: length is breaking
        } finally {
            changeContext.clear();
        }

        List<VersionView> history = standardService.history(scope.tenant(), created.id());
        assertThat(history).hasSize(2);
        VersionView v2 = history.get(1);
        assertThat(v2.version()).isEqualTo("2.0.0");
        assertThat(v2.parentHash()).isEqualTo(hashV1);              // lineage: child -> parent
        assertThat(v2.contentHash()).isNotEqualTo(hashV1);          // identity: distinct content
        assertThat(v2.author()).isEqualTo("alice");                 // who
        assertThat(v2.message()).isEqualTo("widen the account number"); // why
        assertThat(standardService.verify(scope.tenant(), created.id(), "2.0.0")).isTrue();
    }
}
