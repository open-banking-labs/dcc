package cn.org.openbanking.dcc.web.standard;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import cn.org.openbanking.dcc.AbstractIntegrationTest;
import cn.org.openbanking.dcc.application.standard.DataStandardService;
import cn.org.openbanking.dcc.application.tenant.TenantApplicationService;
import cn.org.openbanking.dcc.application.tenant.TenantEnvironmentService;
import cn.org.openbanking.dcc.application.tenant.TenantService;
import cn.org.openbanking.dcc.core.common.error.ConflictException;
import cn.org.openbanking.dcc.core.standard.StandardContent;
import cn.org.openbanking.dcc.core.standard.reference.ReferenceChecker;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Verifies the "delete only when unreferenced" rule, using a stand-in
 * {@link ReferenceChecker} that reports a reference (later phases register the
 * real table/interface/template checkers).
 */
class DataStandardReferenceCheckIntegrationTest extends AbstractIntegrationTest {

    @TestConfiguration
    static class BlockingReferenceConfiguration {
        @Bean
        ReferenceChecker blockingReferenceChecker() {
            return (tenantId, standardId) -> List.of("table structure ACCOUNT.acctno (test double)");
        }
    }

    @Autowired
    private TenantService tenantService;
    @Autowired
    private TenantEnvironmentService environmentService;
    @Autowired
    private TenantApplicationService applicationService;
    @Autowired
    private DataStandardService standardService;

    @Test
    void deleteIsRejectedWhenStandardIsReferenced() {
        String tenant = "t-" + UUID.randomUUID();
        tenantService.create(new TenantService.CreateCommand(tenant, "Tenant", null));
        var environment = environmentService.create(tenant,
                new TenantEnvironmentService.CreateCommand("DEV", "Development", null, 1));
        var application = applicationService.create(tenant,
                new TenantApplicationService.CreateCommand("core", "Core", null));
        var standard = standardService.create(tenant, environment.id(), application.id(),
                new DataStandardService.CreateCommand("acctno", null,
                        new StandardContent("账号", null, "VARCHAR", 32, null, true, null, null, null, null)));

        assertThatThrownBy(() -> standardService.delete(tenant, standard.id()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("referenced");
    }
}
