package cn.org.openbanking.dcc.web.id;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import cn.org.openbanking.dcc.AbstractIntegrationTest;
import cn.org.openbanking.dcc.application.interfaceapi.InterfaceService;
import cn.org.openbanking.dcc.application.standard.DataStandardService;
import cn.org.openbanking.dcc.application.template.TemplateService;
import cn.org.openbanking.dcc.application.tenant.TenantApplicationService;
import cn.org.openbanking.dcc.application.tenant.TenantEnvironmentService;
import cn.org.openbanking.dcc.application.tenant.TenantService;
import cn.org.openbanking.dcc.core.common.error.ConflictException;
import cn.org.openbanking.dcc.core.common.error.ValidationException;
import cn.org.openbanking.dcc.core.interfaceapi.content.InterfaceContent;
import cn.org.openbanking.dcc.core.standard.StandardContent;
import cn.org.openbanking.dcc.core.template.content.TemplateContent;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** End-to-end checks for generated tenant/application/environment/artifact identifiers. */
class IdentifierIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TenantService tenantService;
    @Autowired
    private TenantEnvironmentService environmentService;
    @Autowired
    private TenantApplicationService applicationService;
    @Autowired
    private DataStandardService standardService;
    @Autowired
    private InterfaceService interfaceService;
    @Autowired
    private TemplateService templateService;

    private record Scope(long tenantId, String tenantCode, long environmentId, long applicationId, int appNo) {
    }

    private Scope newScope() {
        var tenant = tenantService.create(new TenantService.CreateCommand(null, "Auto tenant", null));
        var environment = environmentService.create(tenant.code(),
                new TenantEnvironmentService.CreateCommand(null, "Development", null, 1));
        var application = applicationService.create(tenant.code(),
                new TenantApplicationService.CreateCommand(null, "Core", null));
        return new Scope(tenant.id(), tenant.code(), environment.id(), application.id(), application.appNo());
    }

    @Test
    void scopeCodesAndApplicationNumbersAreGenerated() {
        Scope scope = newScope();

        assertThat(scope.tenantCode()).matches("^T[0-9]{3}$");
        assertThat(scope.tenantId()).isBetween(1L, 999L);
        assertThat(scope.appNo()).isBetween(1, 999);
        assertThat(environmentService.list(scope.tenantCode()))
                .extracting(TenantEnvironmentService.EnvironmentView::code)
                .allMatch(code -> code.matches("^E[0-9]{3}$"));
        assertThat(applicationService.list(scope.tenantCode()))
                .extracting(TenantApplicationService.ApplicationView::code)
                .contains("A" + String.format("%03d", scope.appNo()));
    }

    @Test
    void artifactCodesFollowTheTypeTenantApplicationSequenceFormat() {
        Scope scope = newScope();
        String prefix = String.format("%03d%03d", scope.tenantId(), scope.appNo());

        var standard = standardService.create(scope.tenantCode(), scope.environmentId(), scope.applicationId(),
                new DataStandardService.CreateCommand(null, null, standardContent("账号")));
        assertThat(standard.code()).isEqualTo("S" + prefix + "00000001");

        var secondStandard = standardService.create(scope.tenantCode(), scope.environmentId(), scope.applicationId(),
                new DataStandardService.CreateCommand(null, null, standardContent("户名")));
        assertThat(secondStandard.code()).isEqualTo("S" + prefix + "00000002");

        var definition = interfaceService.create(scope.tenantCode(), scope.environmentId(), scope.applicationId(),
                new InterfaceService.CreateCommand(null, null, new InterfaceContent("转账", null, null, null, null,
                        List.of(), List.of())));
        assertThat(definition.interfaceNo()).isEqualTo("I" + prefix + "00000001");

        var template = templateService.create(scope.tenantCode(), scope.environmentId(), scope.applicationId(),
                new TemplateService.CreateCommand(null, new TemplateContent("模板", null, List.of())));
        assertThat(template.code()).isEqualTo("M" + prefix + "00000001");
    }

    @Test
    void manualCodesArePreservedAndReservedFormatsAreRejected() {
        Scope scope = newScope();
        var standard = standardService.create(scope.tenantCode(), scope.environmentId(), scope.applicationId(),
                new DataStandardService.CreateCommand("CUST_NAME", null, standardContent("客户名")));
        assertThat(standard.code()).isEqualTo("CUST_NAME");

        assertThatThrownBy(() -> standardService.create(scope.tenantCode(), scope.environmentId(),
                scope.applicationId(), new DataStandardService.CreateCommand("CUST_NAME", null,
                        standardContent("重复"))))
                .isInstanceOf(ConflictException.class);

        assertThatThrownBy(() -> standardService.create(scope.tenantCode(), scope.environmentId(),
                scope.applicationId(), new DataStandardService.CreateCommand("S00100100000001", null,
                        standardContent("保留"))))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("reserved");
    }

    private static StandardContent standardContent(String name) {
        return new StandardContent(name, null, "VARCHAR", 32, null, true, null, null, null, "123456");
    }
}
