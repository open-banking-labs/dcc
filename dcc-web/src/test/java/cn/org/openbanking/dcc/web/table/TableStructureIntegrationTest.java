package cn.org.openbanking.dcc.web.table;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import cn.org.openbanking.dcc.AbstractIntegrationTest;
import cn.org.openbanking.dcc.application.standard.DataStandardService;
import cn.org.openbanking.dcc.application.table.TableStructureService;
import cn.org.openbanking.dcc.application.table.TableStructureService.CreateCommand;
import cn.org.openbanking.dcc.application.table.TableStructureService.UpdateCommand;
import cn.org.openbanking.dcc.application.tenant.TenantApplicationService;
import cn.org.openbanking.dcc.application.tenant.TenantEnvironmentService;
import cn.org.openbanking.dcc.application.tenant.TenantService;
import cn.org.openbanking.dcc.core.common.error.ConflictException;
import cn.org.openbanking.dcc.core.common.error.ResourceNotFoundException;
import cn.org.openbanking.dcc.core.standard.StandardContent;
import cn.org.openbanking.dcc.core.standard.version.VersionChangeType;
import cn.org.openbanking.dcc.core.table.content.TableColumn;
import cn.org.openbanking.dcc.core.table.content.TableContent;
import cn.org.openbanking.dcc.core.table.content.TableIndex;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** End-to-end tests for table structures: versioning, DDL and reference guarding. */
class TableStructureIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TenantService tenantService;
    @Autowired
    private TenantEnvironmentService environmentService;
    @Autowired
    private TenantApplicationService applicationService;
    @Autowired
    private DataStandardService standardService;
    @Autowired
    private TableStructureService tableService;

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

    private static StandardContent standardContent() {
        return new StandardContent("账号", "account number", "VARCHAR", 32, null, true, null, null, null, "123456");
    }

    private static TableColumn acctNoColumn(Long standardId) {
        // type is left null: it must be resolved from the referenced standard
        return new TableColumn("acct_no", standardId, "acctno", null, null, null, false, true, null, 1, "账号");
    }

    @Test
    void tableVersioningDdlAndStandardDeleteGuard() {
        Scope scope = newScope();
        var standard = standardService.create(scope.tenant(), scope.environmentId(), scope.applicationId(),
                new DataStandardService.CreateCommand("acctno", "account", standardContent()));

        TableContent v1 = new TableContent("Account", "账户表", "acct_no",
                List.of(acctNoColumn(standard.id())),
                List.of(new TableIndex("ix_account_acct_no", true, List.of("acct_no"))));

        var table = tableService.create(scope.tenant(), scope.environmentId(), scope.applicationId(),
                new CreateCommand("account", v1));
        assertThat(table.currentVersion()).isEqualTo("1.0.0");
        // physical type resolved from the referenced standard into the snapshot
        assertThat(table.content().columns().get(0).dataType()).isEqualTo("VARCHAR");
        assertThat(table.content().columns().get(0).length()).isEqualTo(32);

        var ddl = tableService.createDdl(scope.tenant(), table.id(), "public");
        assertThat(ddl.ddl()).contains("CREATE TABLE \"public\".\"account\"");
        assertThat(ddl.ddl()).contains("\"acct_no\" VARCHAR(32) NOT NULL");
        assertThat(ddl.flywayFileName()).endsWith("__create_account.sql");

        // adding a column is a backward-compatible (MINOR) change
        TableColumn nameColumn = new TableColumn("name", null, null, "VARCHAR", 64, null, true, false, null, 2, null);
        TableContent v2 = new TableContent("Account", "账户表", "acct_no",
                List.of(acctNoColumn(standard.id()), nameColumn), List.of());
        var updated = tableService.update(scope.tenant(), table.id(), new UpdateCommand(v2));
        assertThat(updated.currentVersion()).isEqualTo("1.1.0");

        assertThat(tableService.history(scope.tenant(), table.id())).hasSize(2);
        assertThat(tableService.diff(scope.tenant(), table.id(), "1.0.0", "1.1.0").changeType())
                .isEqualTo(VersionChangeType.MINOR);

        // rolling back drops the added column -> breaking (MAJOR)
        var rolled = tableService.rollback(scope.tenant(), table.id(), "1.0.0");
        assertThat(rolled.currentVersion()).isEqualTo("2.0.0");
        assertThat(rolled.content().columns()).hasSize(1);

        // the standard is referenced by a table, so it cannot be deleted
        assertThatThrownBy(() -> standardService.delete(scope.tenant(), standard.id()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("referenced");

        // tenant isolation
        Scope other = newScope();
        assertThatThrownBy(() -> tableService.get(other.tenant(), table.id()))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(tableService.search(other.tenant(), null, null, null)).isEmpty();
    }
}
