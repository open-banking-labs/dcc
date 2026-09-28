package cn.org.openbanking.dcc.web.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import cn.org.openbanking.dcc.AbstractIntegrationTest;
import cn.org.openbanking.dcc.application.standard.DataStandardService;
import cn.org.openbanking.dcc.application.template.TemplateService;
import cn.org.openbanking.dcc.application.template.TemplateService.CreateCommand;
import cn.org.openbanking.dcc.application.template.TemplateService.UpdateCommand;
import cn.org.openbanking.dcc.application.tenant.TenantApplicationService;
import cn.org.openbanking.dcc.application.tenant.TenantEnvironmentService;
import cn.org.openbanking.dcc.application.tenant.TenantService;
import cn.org.openbanking.dcc.core.common.error.ConflictException;
import cn.org.openbanking.dcc.core.common.error.ResourceNotFoundException;
import cn.org.openbanking.dcc.core.standard.StandardContent;
import cn.org.openbanking.dcc.core.standard.version.VersionChangeType;
import cn.org.openbanking.dcc.core.template.content.TemplateContent;
import cn.org.openbanking.dcc.core.template.content.TemplateField;
import cn.org.openbanking.dcc.core.template.content.TemplateSection;
import cn.org.openbanking.dcc.core.template.content.TemplateSectionCode;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** End-to-end tests for interface templates: layered content, versioning, references. */
class TemplateIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TenantService tenantService;
    @Autowired
    private TenantEnvironmentService environmentService;
    @Autowired
    private TenantApplicationService applicationService;
    @Autowired
    private DataStandardService standardService;
    @Autowired
    private TemplateService templateService;

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

    private static TemplateContent template(Long standardId, boolean withTrailer) {
        List<TemplateSection> sections = new java.util.ArrayList<>();
        sections.add(new TemplateSection(TemplateSectionCode.HEAD_PROTOCOL, "协议头",
                List.of(new TemplateField("version", null, null, "VARCHAR", 8, null, true, "FRAMEWORK", List.of()))));
        sections.add(new TemplateSection(TemplateSectionCode.HEAD_GATEWAY, "网关头",
                List.of(new TemplateField("gwId", null, null, "VARCHAR", 16, null, true, "GATEWAY", List.of()))));
        sections.add(new TemplateSection(TemplateSectionCode.BODY, "数据体",
                List.of(new TemplateField("acctno", standardId, "acctno", null, null, null, true, "BUSINESS", List.of()))));
        if (withTrailer) {
            sections.add(new TemplateSection(TemplateSectionCode.TRAILER, "尾部",
                    List.of(new TemplateField("checksum", null, null, "VARCHAR", 64, null, true, "CHECK", List.of()))));
        }
        return new TemplateContent("标准模板", "标准接口模板", sections);
    }

    @Test
    void templateLayersAreVersionedAndGuardStandardDeletion() {
        Scope scope = newScope();
        var standard = standardService.create(scope.tenant(), scope.environmentId(), scope.applicationId(),
                new DataStandardService.CreateCommand("acctno", "account",
                        new StandardContent("账号", "account", "VARCHAR", 32, null, true, null, null, null, null)));

        var created = templateService.create(scope.tenant(), scope.environmentId(), scope.applicationId(),
                new CreateCommand("standard-template", template(standard.id(), false)));
        assertThat(created.currentVersion()).isEqualTo("1.0.0");
        // Body field resolved from the referenced standard
        TemplateSection body = created.content().sections().stream()
                .filter(section -> section.code() == TemplateSectionCode.BODY).findFirst().orElseThrow();
        assertThat(body.fields().get(0).dataType()).isEqualTo("VARCHAR");

        // adding the Trailer section -> MINOR
        var updated = templateService.update(scope.tenant(), created.id(),
                new UpdateCommand(template(standard.id(), true)));
        assertThat(updated.currentVersion()).isEqualTo("1.1.0");

        assertThat(templateService.history(scope.tenant(), created.id())).hasSize(2);
        assertThat(templateService.diff(scope.tenant(), created.id(), "1.0.0", "1.1.0").changeType())
                .isEqualTo(VersionChangeType.MINOR);

        // rollback drops the Trailer -> MAJOR
        assertThat(templateService.rollback(scope.tenant(), created.id(), "1.0.0").currentVersion())
                .isEqualTo("2.0.0");

        // the standard is referenced by the template -> delete blocked
        assertThatThrownBy(() -> standardService.delete(scope.tenant(), standard.id()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("referenced");

        // tenant isolation
        Scope other = newScope();
        assertThatThrownBy(() -> templateService.get(other.tenant(), created.id()))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(templateService.search(other.tenant(), null, null, null)).isEmpty();
    }
}
