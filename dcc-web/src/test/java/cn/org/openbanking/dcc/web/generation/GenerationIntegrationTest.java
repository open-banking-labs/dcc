package cn.org.openbanking.dcc.web.generation;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.util.UUID;

import cn.org.openbanking.dcc.AbstractIntegrationTest;
import cn.org.openbanking.dcc.application.generation.GenerationService;
import cn.org.openbanking.dcc.application.interfaceapi.InterfaceService;
import cn.org.openbanking.dcc.application.standard.DataStandardService;
import cn.org.openbanking.dcc.application.tenant.TenantApplicationService;
import cn.org.openbanking.dcc.application.tenant.TenantEnvironmentService;
import cn.org.openbanking.dcc.application.tenant.TenantService;
import cn.org.openbanking.dcc.core.interfaceapi.content.InterfaceContent;
import cn.org.openbanking.dcc.core.interfaceapi.content.InterfaceField;
import cn.org.openbanking.dcc.core.standard.StandardContent;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** End-to-end tests for artifact generation (validation / DTO / OpenAPI / JAR). */
class GenerationIntegrationTest extends AbstractIntegrationTest {

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
    private GenerationService generationService;

    @Test
    void generatesValidationDtoOpenApiAndPerTenantEnvironmentJar() throws Exception {
        String tenant = "t-" + UUID.randomUUID();
        tenantService.create(new TenantService.CreateCommand(tenant, "Tenant " + tenant, null));
        var environment = environmentService.create(tenant,
                new TenantEnvironmentService.CreateCommand("DEV", "Development", null, 1));
        var application = applicationService.create(tenant,
                new TenantApplicationService.CreateCommand("core", "Core", null));

        var standard = standardService.create(tenant, environment.id(), application.id(),
                new DataStandardService.CreateCommand("acctno", "account",
                        new StandardContent("账号", "account", "VARCHAR", 32, null, true, null, null, "[0-9]+", "123")));
        var definition = interfaceService.create(tenant, environment.id(), application.id(),
                new InterfaceService.CreateCommand("IF0001", null, new InterfaceContent("转账", "payment", "/transfer",
                        "转账接口", "transfer",
                        java.util.List.of(new InterfaceField("acctno", standard.id(), "acctno", null, null, null, true,
                                false, java.util.List.of())),
                        java.util.List.of(new InterfaceField("status", null, null, "VARCHAR", 8, null, true, false,
                                java.util.List.of())))));

        var validation = generationService.generateValidation(tenant, standard.id());
        assertThat(validation.content()).contains("public final class AcctnoValidation");
        assertThat(validation.content()).contains("LENGTH = 32");

        var dto = generationService.generateDto(tenant, definition.id());
        assertThat(dto).extracting("fileName").contains("cn/org/openbanking/dcc/generated/dto/IF0001Request.java");

        String openApi = generationService.generateOpenApi(tenant, environment.id(), application.id());
        assertThat(openApi).contains("\"openapi\": \"3.0.3\"");
        assertThat(openApi).contains("/transfer");
        assertThat(openApi).contains("IF0001Request");

        var bundle = generationService.exportJar(tenant, environment.id(), application.id(), "2.0.0");
        assertThat(bundle.fileName()).isEqualTo("dcc-" + tenant + "-env" + environment.id() + "-2.0.0.jar");
        try (var in = new java.util.jar.JarInputStream(new ByteArrayInputStream(bundle.content()))) {
            assertThat(in.getNextJarEntry()).isNotNull();
        }
    }
}
