package cn.org.openbanking.dcc.web.casebook;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import cn.org.openbanking.dcc.AbstractIntegrationTest;
import cn.org.openbanking.dcc.application.casebook.ReferenceCaseService;
import cn.org.openbanking.dcc.application.casebook.ReferenceCaseService.CreateCommand;
import cn.org.openbanking.dcc.application.casebook.ReferenceCaseService.UpdateCommand;
import cn.org.openbanking.dcc.application.standard.DataStandardService;
import cn.org.openbanking.dcc.application.tenant.TenantApplicationService;
import cn.org.openbanking.dcc.application.tenant.TenantEnvironmentService;
import cn.org.openbanking.dcc.application.tenant.TenantService;
import cn.org.openbanking.dcc.core.casebook.ReferenceCaseFeature;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Reference cases end to end (ADR 0005 §D7): adoption creates a tenant draft with provenance, the
 * adoption index flags a stale copy, and the two audiences see different halves of the same rows.
 */
@AutoConfigureMockMvc
class ReferenceCaseIntegrationTest extends AbstractIntegrationTest {

    private static final String SECRET = "dev-only-change-me-please-use-32-bytes";

    private static final String STANDARD_CONTENT =
            "{\"name\":\"账号\",\"dataType\":\"VARCHAR\",\"length\":32,\"required\":true}";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TenantService tenantService;
    @Autowired
    private TenantEnvironmentService environmentService;
    @Autowired
    private TenantApplicationService applicationService;
    @Autowired
    private DataStandardService standardService;
    @Autowired
    private ReferenceCaseService caseService;

    private String token(String subject, String tenant, String... roles) {
        SecretKey key = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(subject)
                .claim("tenant", tenant)
                .claim("roles", List.of(roles))
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    private String newTenant() {
        String tenant = "t-" + UUID.randomUUID();
        tenantService.create(new TenantService.CreateCommand(tenant, "Tenant " + tenant, null));
        return tenant;
    }

    private ReferenceCaseService.CaseView newCase(String code) {
        return caseService.create(new CreateCommand(ReferenceCaseFeature.DATA_STANDARD, code, "数标范例 " + code,
                "示例：账号数据元", STANDARD_CONTENT, "1.0.0", true));
    }

    @Test
    void adoptionCreatesATenantDraftWithProvenanceAndIsIndexed() throws Exception {
        String tenant = newTenant();
        Long environmentId = environmentService.create(tenant,
                new TenantEnvironmentService.CreateCommand("DEV", "Development", null, 1)).id();
        Long applicationId = applicationService.create(tenant,
                new TenantApplicationService.CreateCommand("core", "Core", null)).id();
        String code = "std-" + UUID.randomUUID().toString().substring(0, 8);
        var referenceCase = newCase(code);

        String auth = "Bearer " + token("tenant-admin", tenant, "DCC_TENANT_ADMIN");
        String adopted = mockMvc.perform(post("/api/cases/" + referenceCase.id() + "/adopt")
                        .contentType("application/json")
                        .content("{\"environmentId\":" + environmentId + ",\"applicationId\":" + applicationId + "}")
                        .header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.feature").value("DATA_STANDARD"))
                .andExpect(jsonPath("$.adoptedFrom").value("adopted from case " + code + " v1.0.0"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        // the draft carries the case code, the provenance lives in the immutable version message
        var standards = standardService.search(tenant, environmentId, applicationId, null, null);
        var created = standards.stream().filter(s -> s.code().equals(code)).findFirst().orElseThrow();
        org.assertj.core.api.Assertions.assertThat(created.currentVersion()).isEqualTo("1.0.0");
        var history = standardService.history(tenant, created.id());
        org.assertj.core.api.Assertions.assertThat(history.get(0).message())
                .contains("adopted from case " + code + " v1.0.0");
        org.assertj.core.api.Assertions.assertThat(adopted).contains("data-standard:" + code);

        // the adoption index knows the copy, and it is not stale yet
        mockMvc.perform(get("/api/case-adoptions").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].caseCode").value(code))
                .andExpect(jsonPath("$[0].artifactCode").value(code))
                .andExpect(jsonPath("$[0].adoptedCaseVersion").value("1.0.0"))
                .andExpect(jsonPath("$[0].stale").value(false));

        // the case moves on: the copy is flagged, never rewritten
        caseService.update(referenceCase.id(), new UpdateCommand(null, "示例（修订）", null, "1.1.0", null));
        mockMvc.perform(get("/api/case-adoptions").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].currentCaseVersion").value("1.1.0"))
                .andExpect(jsonPath("$[0].stale").value(true));
        org.assertj.core.api.Assertions.assertThat(standardService.get(tenant, created.id()).currentVersion())
                .isEqualTo("1.0.0");
    }

    @Test
    void theTwoAudiencesSeeDifferentHalvesOfTheSameAdoption() throws Exception {
        String tenant = newTenant();
        Long environmentId = environmentService.create(tenant,
                new TenantEnvironmentService.CreateCommand("DEV", "Development", null, 1)).id();
        Long applicationId = applicationService.create(tenant,
                new TenantApplicationService.CreateCommand("core", "Core", null)).id();
        String code = "reach-" + UUID.randomUUID().toString().substring(0, 8);
        var referenceCase = newCase(code);

        mockMvc.perform(post("/api/cases/" + referenceCase.id() + "/adopt")
                        .contentType("application/json")
                        .content("{\"environmentId\":" + environmentId + ",\"applicationId\":" + applicationId + "}")
                        .header("Authorization", "Bearer " + token("tenant-admin", tenant, "DCC_TENANT_ADMIN")))
                .andExpect(status().isOk());

        // reach is platform-only, and it never carries a tenant artifact identifier
        mockMvc.perform(get("/api/platform/case-adoptions/" + referenceCase.id())
                        .header("Authorization", "Bearer " + token("ops", tenant, "DCC_TENANT_ADMIN")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/platform/case-adoptions/" + referenceCase.id())
                        .header("Authorization", "Bearer " + token("sys", tenant, "DCC_PLATFORM_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tenantId").value(tenant))
                .andExpect(jsonPath("$[0].adoptedCaseVersion").value("1.0.0"))
                .andExpect(jsonPath("$[0].artifactCode").doesNotExist());

        // a reader can browse the library but cannot adopt
        mockMvc.perform(get("/api/cases").header("Authorization", "Bearer " + token("reader", tenant, "DCC_READER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/cases/" + referenceCase.id() + "/adopt")
                        .header("Authorization", "Bearer " + token("reader", tenant, "DCC_READER")))
                .andExpect(status().isForbidden());
    }
}
