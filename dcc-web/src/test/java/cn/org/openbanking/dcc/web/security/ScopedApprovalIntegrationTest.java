package cn.org.openbanking.dcc.web.security;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import cn.org.openbanking.dcc.AbstractIntegrationTest;
import cn.org.openbanking.dcc.application.migration.MigrationService;
import cn.org.openbanking.dcc.application.standard.DataStandardService;
import cn.org.openbanking.dcc.application.tenant.RoleAssignmentService;
import cn.org.openbanking.dcc.application.tenant.TenantApplicationService;
import cn.org.openbanking.dcc.application.tenant.TenantEnvironmentService;
import cn.org.openbanking.dcc.application.tenant.TenantMembershipService;
import cn.org.openbanking.dcc.application.tenant.TenantService;
import cn.org.openbanking.dcc.core.migration.ArtifactType;
import cn.org.openbanking.dcc.core.standard.StandardContent;
import cn.org.openbanking.dcc.core.standard.StandardStatus;
import cn.org.openbanking.dcc.core.tenant.ScopeType;

import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jose.jwk.source.ImmutableSecret;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Scoped approval authority (ADR 0005, closing ADR 0004's known limitation): a grant restricted to
 * one environment authorizes approval of <em>that</em> environment only, resolved from the resource
 * rather than from a request header.
 *
 * <p>Runs with directory mode on, because scoped grants only exist there: they are directory rows,
 * not token claims.
 */
@SpringBootTest(properties = "dcc.security.directory-mode=true")
@AutoConfigureMockMvc
class ScopedApprovalIntegrationTest extends AbstractIntegrationTest {

    private static final String SECRET = "dev-only-change-me-please-use-32-bytes";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TenantService tenantService;
    @Autowired
    private TenantEnvironmentService environmentService;
    @Autowired
    private TenantApplicationService applicationService;
    @Autowired
    private TenantMembershipService memberships;
    @Autowired
    private RoleAssignmentService assignments;
    @Autowired
    private DataStandardService standardService;
    @Autowired
    private MigrationService migrationService;

    private String tenant;
    private Long devEnv;
    private Long uatEnv;
    private Long applicationId;

    private final String devApprover = "dev-approver-" + UUID.randomUUID();
    private final String uatApprover = "uat-approver-" + UUID.randomUUID();
    private final String tenantApprover = "tenant-approver-" + UUID.randomUUID();
    private final String maintainer = "maintainer-" + UUID.randomUUID();
    private final String devMaintainer = "dev-maintainer-" + UUID.randomUUID();

    @BeforeEach
    void seed() {
        tenant = "t-" + UUID.randomUUID();
        tenantService.create(new TenantService.CreateCommand(tenant, "Scoped tenant", null));
        devEnv = environmentService.create(tenant,
                new TenantEnvironmentService.CreateCommand("DEV", "Development", null, 1)).id();
        uatEnv = environmentService.create(tenant,
                new TenantEnvironmentService.CreateCommand("UAT", "Acceptance", null, 2)).id();
        applicationId = applicationService.create(tenant,
                new TenantApplicationService.CreateCommand("core", "Core", null)).id();

        for (String subject : List.of(devApprover, uatApprover, tenantApprover, maintainer, devMaintainer)) {
            memberships.create(new TenantMembershipService.CreateCommand(subject, tenant));
        }
        // environment-scoped approvers, one per surface
        assignments.create(new RoleAssignmentService.CreateCommand(devApprover, tenant, "DCC_APPROVER",
                ScopeType.ENVIRONMENT, "DEV"));
        assignments.create(new RoleAssignmentService.CreateCommand(uatApprover, tenant, "DCC_APPROVER",
                ScopeType.ENVIRONMENT, "UAT"));
        // and a tenant-wide counterpart for comparison
        assignments.create(new RoleAssignmentService.CreateCommand(tenantApprover, tenant, "DCC_APPROVER",
                ScopeType.TENANT, null));
        assignments.create(new RoleAssignmentService.CreateCommand(maintainer, tenant, "DCC_MAINTAINER",
                ScopeType.TENANT, null));
        assignments.create(new RoleAssignmentService.CreateCommand(devMaintainer, tenant, "DCC_MAINTAINER",
                ScopeType.ENVIRONMENT, "DEV"));
    }

    private String token(String subject) {
        SecretKey key = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(subject)
                .claim("tenant", tenant)
                .claim("roles", List.of("DCC_READER"))
                .build();
        return "Bearer " + encoder
                .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    /** Creates a standard in the given environment and submits it for review (as the maintainer). */
    private long submittedStandard(Long environmentId, String code) {
        var created = standardService.create(tenant, environmentId, applicationId,
                new DataStandardService.CreateCommand(code, null,
                        new StandardContent("账号", null, "VARCHAR", 32, null, true, null, null, null, "123")));
        standardService.transition(tenant, created.id(), StandardStatus.PENDING_REVIEW);
        return created.id();
    }

    private void publish(long standardId, String auth, int expectedStatus) throws Exception {
        mockMvc.perform(post("/api/standards/" + standardId + "/status")
                        .param("target", "PUBLISHED").header("Authorization", auth))
                .andExpect(status().is(expectedStatus));
    }

    @Test
    void environmentScopedApproverCanPublishOnlyItsOwnEnvironment() throws Exception {
        long devStandard = submittedStandard(devEnv, "acct-dev");
        long uatStandard = submittedStandard(uatEnv, "acct-uat");

        // a DEV-scoped approver cannot publish into UAT…
        publish(uatStandard, token(devApprover), 403);
        // …but can publish its own environment
        publish(devStandard, token(devApprover), 200);

        // the UAT-scoped approver is the mirror image
        publish(uatStandard, token(uatApprover), 200);
    }

    @Test
    void environmentScopedMaintainerWritesOnlyItsOwnEnvironment() throws Exception {
        long devStandard = submittedStandard(devEnv, "write-dev");
        long uatStandard = submittedStandard(uatEnv, "write-uat");
        String auth = token(devMaintainer);
        String body = "{\"content\":{\"name\":\"账号\",\"dataType\":\"VARCHAR\",\"length\":64,\"required\":true}}";

        // editing into another environment is denied…
        mockMvc.perform(put("/api/standards/" + uatStandard).header("Authorization", auth)
                        .contentType("application/json").content(body))
                .andExpect(status().isForbidden());
        // …its own environment is fine
        mockMvc.perform(put("/api/standards/" + devStandard).header("Authorization", auth)
                        .contentType("application/json").content(body))
                .andExpect(status().isOk());

        // deletion follows the same surface rule
        mockMvc.perform(delete("/api/standards/" + uatStandard).header("Authorization", auth))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/standards/" + devStandard).header("Authorization", auth))
                .andExpect(status().isNoContent());
    }

    @Test
    void delegatedEnvironmentAdministratorActsInsideOneSurfaceOnly() throws Exception {
        String delegate = "delegate-" + UUID.randomUUID();
        memberships.create(new TenantMembershipService.CreateCommand(delegate, tenant));
        assignments.create(new RoleAssignmentService.CreateCommand(delegate, tenant, "DCC_ENV_ADMIN",
                ScopeType.ENVIRONMENT, "DEV"));

        long devStandard = submittedStandard(devEnv, "delegate-dev");
        long uatStandard = submittedStandard(uatEnv, "delegate-uat");
        String auth = token(delegate);
        String body = "{\"content\":{\"name\":\"账号\",\"dataType\":\"VARCHAR\",\"length\":64,\"required\":true}}";

        // data plane inside its surface
        mockMvc.perform(put("/api/standards/" + devStandard).header("Authorization", auth)
                        .contentType("application/json").content(body))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/standards/" + devStandard).header("Authorization", auth))
                .andExpect(status().isNoContent());

        // another surface is out of reach
        mockMvc.perform(put("/api/standards/" + uatStandard).header("Authorization", auth)
                        .contentType("application/json").content(body))
                .andExpect(status().isForbidden());

        // submitting for review IS part of the role, but only inside its surface
        var devDraft = standardService.create(tenant, devEnv, applicationId,
                new DataStandardService.CreateCommand("delegate-draft", null,
                        new StandardContent("账号", null, "VARCHAR", 16, null, true, null, null, null, "1")));
        mockMvc.perform(post("/api/standards/" + devDraft.id() + "/status")
                        .param("target", "PENDING_REVIEW").header("Authorization", auth))
                .andExpect(status().isOk());
        // …while on another surface even that is out of reach
        mockMvc.perform(post("/api/standards/" + uatStandard + "/status")
                        .param("target", "PENDING_REVIEW").header("Authorization", auth))
                .andExpect(status().isForbidden());
        // publishing is never part of the role: approval stays with DCC_APPROVER
        mockMvc.perform(post("/api/standards/" + devDraft.id() + "/status")
                        .param("target", "PUBLISHED").header("Authorization", auth))
                .andExpect(status().isForbidden());

        // and environments stay the tenant administrator's
        mockMvc.perform(post("/api/environments").header("Authorization", auth)
                        .contentType("application/json")
                        .content("{\"code\":\"NEW\",\"name\":\"New\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void tenantWideApproverIsUnaffected() throws Exception {
        long uatStandard = submittedStandard(uatEnv, "acct-wide");

        publish(uatStandard, token(tenantApprover), 200);
    }

    @Test
    void scopedApprovalDoesNotBypassFourEyes() throws Exception {
        // the maintainer authors and submits through REST (so the version's author is bound), then is
        // granted tenant-wide approval: it still cannot approve its own submission
        long authored = submittedStandardAsMaintainer(uatEnv, "acct-self");
        assignments.create(new RoleAssignmentService.CreateCommand(maintainer, tenant, "DCC_APPROVER",
                ScopeType.TENANT, null));

        mockMvc.perform(post("/api/standards/" + authored + "/status")
                        .param("target", "PUBLISHED").header("Authorization", token(maintainer)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("four-eyes")));

        // a different approver on the same surface can
        publish(authored, token(uatApprover), 200);
    }

    /** Authors and submits a standard through REST as the maintainer, so the version has a real author. */
    private long submittedStandardAsMaintainer(Long environmentId, String code) throws Exception {
        var response = mockMvc.perform(post("/api/standards")
                        .param("environmentId", String.valueOf(environmentId))
                        .param("applicationId", String.valueOf(applicationId))
                        .header("Authorization", token(maintainer))
                        .contentType("application/json")
                        .content("{\"code\":\"" + code
                                + "\",\"content\":{\"name\":\"账号\",\"dataType\":\"VARCHAR\",\"length\":32,"
                                + "\"required\":true}}"))
                .andReturn().getResponse();
        String created = response.getContentAsString(StandardCharsets.UTF_8);
        org.assertj.core.api.Assertions.assertThat(response.getStatus())
                .as("create response: status=%s type=%s body=%s", response.getStatus(),
                        response.getContentType(), created)
                .isEqualTo(201);
        long id = ((Number) JsonPath.parse(created).read("$.id")).longValue();
        mockMvc.perform(post("/api/standards/" + id + "/status")
                        .param("target", "PENDING_REVIEW").header("Authorization", token(maintainer)))
                .andExpect(status().isOk());
        return id;
    }

    @Test
    void migrationApprovalIsDecidedByTheTargetEnvironment() throws Exception {
        // an artifact living in DEV, migrated into UAT: approval authority follows the *target*
        var standard = standardService.create(tenant, devEnv, applicationId,
                new DataStandardService.CreateCommand("acct-mig", null,
                        new StandardContent("账号", null, "VARCHAR", 32, null, true, null, null, null, "123")));
        standardService.transition(tenant, standard.id(), StandardStatus.PENDING_REVIEW);
        standardService.transition(tenant, standard.id(), StandardStatus.PUBLISHED);

        var order = migrationService.create(tenant, new MigrationService.CreateCommand(
                ArtifactType.DATA_STANDARD, standard.id(), devEnv, uatEnv, "release"));
        migrationService.submit(tenant, order.id());

        // the DEV-scoped approver is the artifact's own environment, but not the target: denied
        mockMvc.perform(post("/api/migrations/" + order.id() + "/approve")
                        .header("Authorization", token(devApprover)))
                .andExpect(status().isForbidden());

        // the UAT-scoped approver decides
        mockMvc.perform(post("/api/migrations/" + order.id() + "/approve")
                        .header("Authorization", token(uatApprover)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    void submittingForReviewIsAMaintainerStepNotAnApproverStep() throws Exception {
        long uatStandard = submittedStandard(uatEnv, "acct-submit");
        long devStandard = submittedStandard(devEnv, "acct-submit-dev");

        // the maintainer can push its own draft into review (artifact.write), on any environment
        var draft = standardService.create(tenant, uatEnv, applicationId,
                new DataStandardService.CreateCommand("fresh", null,
                        new StandardContent("账号", null, "VARCHAR", 16, null, true, null, null, null, "1")));
        mockMvc.perform(post("/api/standards/" + draft.id() + "/status")
                        .param("target", "PENDING_REVIEW").header("Authorization", token(maintainer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_REVIEW"));

        // a caller holding only approval authority cannot submit: submitting needs artifact.write
        mockMvc.perform(post("/api/standards/" + devStandard + "/status")
                        .param("target", "PENDING_REVIEW")
                        .header("Authorization", token(devApprover)))
                .andExpect(status().isForbidden());

        publish(uatStandard, token(uatApprover), 200);
    }
}
