package cn.org.openbanking.dcc.web.interfaceapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import cn.org.openbanking.dcc.AbstractIntegrationTest;
import cn.org.openbanking.dcc.application.interfaceapi.InterfaceService;
import cn.org.openbanking.dcc.application.interfaceapi.InterfaceService.CreateCommand;
import cn.org.openbanking.dcc.application.interfaceapi.InterfaceService.UpdateCommand;
import cn.org.openbanking.dcc.application.standard.DataStandardService;
import cn.org.openbanking.dcc.application.tenant.TenantApplicationService;
import cn.org.openbanking.dcc.application.tenant.TenantEnvironmentService;
import cn.org.openbanking.dcc.application.tenant.TenantService;
import cn.org.openbanking.dcc.core.common.error.ConflictException;
import cn.org.openbanking.dcc.core.common.error.ResourceNotFoundException;
import cn.org.openbanking.dcc.core.interfaceapi.content.InterfaceContent;
import cn.org.openbanking.dcc.core.interfaceapi.content.InterfaceField;
import cn.org.openbanking.dcc.core.standard.StandardContent;
import cn.org.openbanking.dcc.core.standard.version.VersionChangeType;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** End-to-end tests for business interfaces: multi-reference fields, uniqueness, versioning. */
class InterfaceIntegrationTest extends AbstractIntegrationTest {

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

    private static InterfaceField standardField(String name, Long standardId) {
        return new InterfaceField(name, standardId, "acctno", null, null, null, true, false, List.of());
    }

    @Test
    void interfaceFieldsCanReferenceTheSameStandardTwiceAndAreVersioned() {
        Scope scope = newScope();
        var standard = standardService.create(scope.tenant(), scope.environmentId(), scope.applicationId(),
                new DataStandardService.CreateCommand("acctno", "account",
                        new StandardContent("账号", "account", "VARCHAR", 32, null, true, null, null, null, null)));

        // acctno and acctno1 both reference the same standard
        InterfaceContent v1 = new InterfaceContent("转账", "payment", "/transfer", "转账接口", "transfer",
                List.of(standardField("acctno", standard.id()), standardField("acctno1", standard.id())),
                List.of(new InterfaceField("status", null, null, "VARCHAR", 8, null, true, false, List.of())));

        var created = interfaceService.create(scope.tenant(), scope.environmentId(), scope.applicationId(),
                new CreateCommand("IF0001", null, v1));
        assertThat(created.currentVersion()).isEqualTo("1.0.0");
        assertThat(created.content().input()).hasSize(2);
        // both fields carry the standard id and the resolved type
        assertThat(created.content().input()).allMatch(f -> standard.id().equals(f.standardId()));
        assertThat(created.content().input()).allMatch(f -> "VARCHAR".equals(f.dataType()));

        // interface number is unique within tenant + environment
        assertThatThrownBy(() -> interfaceService.create(scope.tenant(), scope.environmentId(), scope.applicationId(),
                new CreateCommand("IF0001", null, v1)))
                .isInstanceOf(ConflictException.class);

        // add an output field -> MINOR
        InterfaceContent v2 = new InterfaceContent("转账", "payment", "/transfer", "转账接口", "transfer",
                List.of(standardField("acctno", standard.id()), standardField("acctno1", standard.id())),
                List.of(new InterfaceField("status", null, null, "VARCHAR", 8, null, true, false, List.of()),
                        new InterfaceField("amount", null, null, "NUMERIC", 18, 2, true, false, List.of())));
        var updated = interfaceService.update(scope.tenant(), created.id(), new UpdateCommand(null, v2));
        assertThat(updated.currentVersion()).isEqualTo("1.1.0");

        assertThat(interfaceService.history(scope.tenant(), created.id())).hasSize(2);
        assertThat(interfaceService.diff(scope.tenant(), created.id(), "1.0.0", "1.1.0").changeType())
                .isEqualTo(VersionChangeType.MINOR);

        // the standard is referenced by the interface -> delete blocked
        assertThatThrownBy(() -> standardService.delete(scope.tenant(), standard.id()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("referenced");

        // tenant isolation
        Scope other = newScope();
        assertThatThrownBy(() -> interfaceService.get(other.tenant(), created.id()))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(interfaceService.search(other.tenant(), null, null, null)).isEmpty();
    }
}
