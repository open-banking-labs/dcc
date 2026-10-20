package cn.org.openbanking.dcc.web.standard;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import cn.org.openbanking.dcc.AbstractIntegrationTest;
import cn.org.openbanking.dcc.application.standard.DataStandardService;
import cn.org.openbanking.dcc.application.standard.DataStandardService.CreateCommand;
import cn.org.openbanking.dcc.application.standard.DataStandardService.StandardSummaryView;
import cn.org.openbanking.dcc.application.tenant.TenantApplicationService;
import cn.org.openbanking.dcc.application.tenant.TenantEnvironmentService;
import cn.org.openbanking.dcc.application.tenant.TenantService;
import cn.org.openbanking.dcc.core.standard.StandardContent;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;

/** Paged console search, keyword matching and batch summaries. */
class DataStandardQueryIntegrationTest extends AbstractIntegrationTest {

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
    void keywordMatchesCodeOrNameCaseInsensitively() {
        Scope scope = newScope();
        standardService.create(scope.tenant(), scope.environmentId(), scope.applicationId(),
                new CreateCommand("ACCT_NO", "account", content("账号")));
        standardService.create(scope.tenant(), scope.environmentId(), scope.applicationId(),
                new CreateCommand("CUST_NAME", "customer", content("客户名称")));

        Page<StandardSummaryView> byCode = query(scope, "acct");
        assertThat(byCode.getTotalElements()).isEqualTo(1);
        assertThat(byCode.getContent()).extracting(StandardSummaryView::code).containsExactly("ACCT_NO");

        Page<StandardSummaryView> byName = query(scope, "客户");
        assertThat(byName.getContent()).extracting(StandardSummaryView::code).containsExactly("CUST_NAME");

        Page<StandardSummaryView> mixedCase = query(scope, "AcCt_No");
        assertThat(mixedCase.getContent()).extracting(StandardSummaryView::code).containsExactly("ACCT_NO");

        // A literal wildcard must not turn into "match everything".
        assertThat(query(scope, "%").getTotalElements()).isZero();
    }

    @Test
    void pagedSearchReturnsStableOrderAndTotals() {
        Scope scope = newScope();
        for (int i = 0; i < 25; i++) {
            standardService.create(scope.tenant(), scope.environmentId(), scope.applicationId(),
                    new CreateCommand(String.format("S%02d", i), null, content("标准 " + i)));
        }

        Page<StandardSummaryView> first = standardService.query(scope.tenant(), scope.environmentId(),
                scope.applicationId(), null, null, null, 0, 10);
        assertThat(first.getTotalElements()).isEqualTo(25);
        assertThat(first.getTotalPages()).isEqualTo(3);
        assertThat(first.getNumber()).isZero();
        assertThat(first.getContent()).hasSize(10);
        assertThat(first.getContent().get(0).code()).isEqualTo("S00");

        Page<StandardSummaryView> last = standardService.query(scope.tenant(), scope.environmentId(),
                scope.applicationId(), null, null, null, 2, 10);
        assertThat(last.getContent()).hasSize(5);
        assertThat(last.getContent().get(4).code()).isEqualTo("S24");

        // The size cap protects the database from a hostile page size.
        Page<StandardSummaryView> capped = standardService.query(scope.tenant(), scope.environmentId(),
                scope.applicationId(), null, null, null, 0, 10_000);
        assertThat(capped.getSize()).isEqualTo(100);
    }

    @Test
    void summariesAreTenantScopedAndKeepRequestedOrder() {
        Scope own = newScope();
        Scope other = newScope();
        var a = standardService.create(own.tenant(), own.environmentId(), own.applicationId(),
                new CreateCommand("A", null, content("A")));
        var b = standardService.create(own.tenant(), own.environmentId(), own.applicationId(),
                new CreateCommand("B", null, content("B")));
        var foreign = standardService.create(other.tenant(), other.environmentId(), other.applicationId(),
                new CreateCommand("FOREIGN", null, content("Foreign")));

        List<StandardSummaryView> summaries = standardService.summaries(own.tenant(),
                List.of(b.id(), foreign.id(), a.id()));
        assertThat(summaries).extracting(StandardSummaryView::code).containsExactly("B", "A");
    }

    @Test
    void logicallyDeletedStandardsLeaveTheQueryAndSummaryViews() {
        Scope scope = newScope();
        var live = standardService.create(scope.tenant(), scope.environmentId(), scope.applicationId(),
                new CreateCommand("LIVE", null, content("Live")));
        var deleted = standardService.create(scope.tenant(), scope.environmentId(), scope.applicationId(),
                new CreateCommand("GONE", null, content("Gone")));
        standardService.delete(scope.tenant(), deleted.id());

        assertThat(query(scope, null).getContent())
                .extracting(StandardSummaryView::id)
                .containsExactly(live.id());
        assertThat(standardService.summaries(scope.tenant(), List.of(deleted.id()))).isEmpty();
    }

    private Page<StandardSummaryView> query(Scope scope, String keyword) {
        return standardService.query(scope.tenant(), scope.environmentId(), scope.applicationId(),
                keyword, null, null, 0, 20);
    }

    private static StandardContent content(String name) {
        return new StandardContent(name, null, "VARCHAR", 32, null, false, null, null, null, null);
    }
}
