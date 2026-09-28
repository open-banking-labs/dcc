package cn.org.openbanking.dcc.web.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import cn.org.openbanking.dcc.AbstractIntegrationTest;
import cn.org.openbanking.dcc.application.cli.CliService;
import cn.org.openbanking.dcc.application.cli.CliService.ExportFile;
import cn.org.openbanking.dcc.application.interfaceapi.InterfaceService;
import cn.org.openbanking.dcc.application.standard.DataStandardService;
import cn.org.openbanking.dcc.application.standard.DataStandardService.UpdateCommand;
import cn.org.openbanking.dcc.application.table.TableStructureService;
import cn.org.openbanking.dcc.application.tenant.TenantApplicationService;
import cn.org.openbanking.dcc.application.tenant.TenantEnvironmentService;
import cn.org.openbanking.dcc.application.tenant.TenantService;
import cn.org.openbanking.dcc.core.interfaceapi.content.InterfaceContent;
import cn.org.openbanking.dcc.core.interfaceapi.content.InterfaceField;
import cn.org.openbanking.dcc.core.migration.ArtifactType;
import cn.org.openbanking.dcc.core.standard.StandardContent;
import cn.org.openbanking.dcc.core.table.content.TableColumn;
import cn.org.openbanking.dcc.core.table.content.TableContent;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Verifies the command-shaped surface behind {@code /cli/v1/*}. */
class CliSurfaceIntegrationTest extends AbstractIntegrationTest {

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
    @Autowired
    private InterfaceService interfaceService;
    @Autowired
    private CliService cli;

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
    void hashDiffBumpValidateAndExport() {
        Scope scope = newScope();
        var standard = standardService.create(scope.tenant(), scope.environmentId(), scope.applicationId(),
                new DataStandardService.CreateCommand("acctno", "account",
                        new StandardContent("账号", "account", "VARCHAR", 32, null, true, null, null, null, null)));
        standardService.update(scope.tenant(), standard.id(), new UpdateCommand(null,
                new StandardContent("账号", "account", "VARCHAR", 64, null, true, null, null, null, null)));

        assertThat(cli.hash(scope.tenant(), ArtifactType.DATA_STANDARD, standard.id(), "1.0.0")).startsWith("sha256:");
        assertThat(cli.diff(scope.tenant(), ArtifactType.DATA_STANDARD, standard.id(), "1.0.0", "2.0.0")).isNotEmpty();
        assertThat(cli.bump(scope.tenant(), ArtifactType.DATA_STANDARD, standard.id(), "1.0.0", "2.0.0").level())
                .isEqualTo("MAJOR");
        assertThat(cli.validate(scope.tenant(), ArtifactType.DATA_STANDARD, standard.id(), "1.0.0").valid()).isTrue();

        List<ExportFile> javaFiles = cli.export(scope.tenant(), ArtifactType.DATA_STANDARD, standard.id(), "1.0.0", "java");
        assertThat(javaFiles).extracting(ExportFile::path)
                .contains("cn/org/openbanking/dcc/generated/validation/AcctnoValidation.java");

        // a table exports SQL (a Flyway script stamped with the source hash)
        var table = tableService.create(scope.tenant(), scope.environmentId(), scope.applicationId(),
                new TableStructureService.CreateCommand("account", new TableContent("Account", "账户表", "acct_no",
                        List.of(new TableColumn("acct_no", null, null, "VARCHAR", 32, null, false, true, null, 1, "账号")),
                        List.of())));
        List<ExportFile> sqlFiles = cli.export(scope.tenant(), ArtifactType.TABLE_STRUCTURE, table.id(), "1.0.0", "sql");
        assertThat(sqlFiles).hasSize(1);
        assertThat(sqlFiles.get(0).path()).endsWith("__create_account.sql");
        assertThat(sqlFiles.get(0).content()).contains("-- generated from model@sha256:");

        // an interface exports DTOs
        var definition = interfaceService.create(scope.tenant(), scope.environmentId(), scope.applicationId(),
                new InterfaceService.CreateCommand("IF0001", null, new InterfaceContent("转账", "payment", "/transfer",
                        "转账接口", "transfer",
                        List.of(new InterfaceField("acctno", standard.id(), "acctno", null, null, null, true, false,
                                List.of())),
                        List.of(new InterfaceField("status", null, null, "VARCHAR", 8, null, true, false,
                                List.of())))));
        assertThat(cli.export(scope.tenant(), ArtifactType.INTERFACE, definition.id(), "1.0.0", "java"))
                .extracting(ExportFile::path)
                .contains("cn/org/openbanking/dcc/generated/dto/IF0001Request.java");

        assertThat(cli.drift(scope.tenant(), scope.environmentId(), scope.applicationId())).isEmpty();
    }
}
