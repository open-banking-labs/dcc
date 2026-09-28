package cn.org.openbanking.dcc.web.versioning;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import cn.org.openbanking.dcc.AbstractIntegrationTest;
import cn.org.openbanking.dcc.application.standard.DataStandardService;
import cn.org.openbanking.dcc.application.standard.DataStandardService.UpdateCommand;
import cn.org.openbanking.dcc.application.tenant.TenantApplicationService;
import cn.org.openbanking.dcc.application.tenant.TenantEnvironmentService;
import cn.org.openbanking.dcc.application.tenant.TenantService;
import cn.org.openbanking.dcc.application.versioning.VersioningService;
import cn.org.openbanking.dcc.core.migration.ArtifactType;
import cn.org.openbanking.dcc.core.standard.StandardContent;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Verifies the dcc hash/diff/bump/drift engineering surface. */
class VersioningIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TenantService tenantService;
    @Autowired
    private TenantEnvironmentService environmentService;
    @Autowired
    private TenantApplicationService applicationService;
    @Autowired
    private DataStandardService standardService;
    @Autowired
    private VersioningService versioning;

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
    void hashDiffBumpAndDriftBehaveAsAdvisoryTools() {
        Scope scope = newScope();
        var standard = standardService.create(scope.tenant(), scope.environmentId(), scope.applicationId(),
                new DataStandardService.CreateCommand("acctno", "account", content(32)));
        standardService.update(scope.tenant(), standard.id(), new UpdateCommand(null, content(64)));

        // hash: identity of a stored version
        String hash = versioning.irHash(scope.tenant(), ArtifactType.DATA_STANDARD, standard.id(), "1.0.0");
        assertThat(hash).startsWith("sha256:");

        // hash: raw content, same identity regardless of nothing — deterministic
        String rawHash = versioning.hashContent(ArtifactType.DATA_STANDARD,
                "{\"name\":\"账号\",\"description\":\"account\",\"dataType\":\"VARCHAR\",\"length\":32,"
                        + "\"scale\":null,\"required\":true,\"defaultValue\":null,\"enumValues\":null,"
                        + "\"regex\":null,\"exampleValue\":null}");
        assertThat(rawHash).isEqualTo(hash);

        // diff: non-empty change list
        assertThat(versioning.diff(scope.tenant(), ArtifactType.DATA_STANDARD, standard.id(), "1.0.0", "2.0.0"))
                .isNotEmpty();

        // bump: advisory; a widening length is a breaking (MAJOR) change
        assertThat(versioning.suggest(scope.tenant(), ArtifactType.DATA_STANDARD, standard.id(), "1.0.0", "2.0.0")
                .level()).isEqualTo("MAJOR");
        assertThat(versioning.suggest(scope.tenant(), ArtifactType.DATA_STANDARD, standard.id(), "1.0.0", "1.0.0")
                .level()).isEqualTo("NONE");

        // drift: everything verifies, so no findings
        assertThat(versioning.drift(scope.tenant(), scope.environmentId(), scope.applicationId())).isEmpty();
    }
}
