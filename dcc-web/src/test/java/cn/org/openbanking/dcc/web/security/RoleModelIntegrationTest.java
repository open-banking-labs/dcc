package cn.org.openbanking.dcc.web.security;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import cn.org.openbanking.dcc.application.tenant.RoleAssignmentService;
import cn.org.openbanking.dcc.application.tenant.TenantApplicationService;
import cn.org.openbanking.dcc.application.tenant.TenantEnvironmentService;
import cn.org.openbanking.dcc.application.tenant.TenantService;

import com.jayway.jsonpath.JsonPath;
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
 * End-to-end checks of the five-role model (ADR 0005) against the real filter chain: effective
 * permissions, the platform/tenant permission split, tenant-scope forcing on grants and the
 * four-eyes rule.
 */
@AutoConfigureMockMvc
class RoleModelIntegrationTest extends AbstractIntegrationTest {

    private static final String SECRET = "dev-only-change-me-please-use-32-bytes";

    private static final String STANDARD_BODY = """
            {"code":"acctno","content":{"name":"账号","dataType":"VARCHAR","length":32,"required":true}}
            """;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TenantService tenantService;
    @Autowired
    private TenantEnvironmentService environmentService;
    @Autowired
    private TenantApplicationService applicationService;
    @Autowired
    private RoleAssignmentService assignments;

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

    @Test
    void effectivePermissionsComeFromTheServerNotTheClaim() throws Exception {
        String tenant = newTenant();

        // the claim only carries the role; /api/me returns the expanded permission points
        mockMvc.perform(get("/api/me").header("Authorization", "Bearer " + token("reader", tenant, "DCC_READER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenant").value(tenant))
                .andExpect(jsonPath("$.permissions", hasItem("artifact.read")))
                .andExpect(jsonPath("$.permissions", not(hasItem("artifact.write"))));

        // and the legacy hat keeps its historical breadth
        mockMvc.perform(get("/api/me").header("Authorization", "Bearer " + token("legacy", tenant, "DCC_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions", hasItem("tenant.admit")))
                .andExpect(jsonPath("$.permissions", hasItem("artifact.write")));
    }

    @Test
    void platformAdministrationIsNotReachableWithTenantAuthorities() throws Exception {
        String tenant = newTenant();

        mockMvc.perform(get("/api/tenants")
                        .header("Authorization", "Bearer " + token("ops", tenant, "DCC_TENANT_ADMIN")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/platform/audit")
                        .header("Authorization", "Bearer " + token("ops", tenant, "DCC_TENANT_ADMIN")))
                .andExpect(status().isForbidden());
        // cross-tenant directory search is platform administration: a tenant admin must not page
        // through every tenant's memberships/grants
        mockMvc.perform(get("/api/directory/memberships")
                        .header("Authorization", "Bearer " + token("ops", tenant, "DCC_TENANT_ADMIN")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/directory/role-assignments")
                        .header("Authorization", "Bearer " + token("ops", tenant, "DCC_TENANT_ADMIN")))
                .andExpect(status().isForbidden());

        // the platform administrator holds no data-plane permission at all
        mockMvc.perform(get("/api/standards")
                        .header("Authorization", "Bearer " + token("sys", tenant, "DCC_PLATFORM_ADMIN")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/tenants")
                        .header("Authorization", "Bearer " + token("sys", tenant, "DCC_PLATFORM_ADMIN")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/platform/audit")
                        .header("Authorization", "Bearer " + token("sys", tenant, "DCC_PLATFORM_ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void tenantAdministratorCanOnlyGrantInsideItsOwnTenant() throws Exception {
        String tenant = newTenant();
        String foreign = newTenant();
        String auth = "Bearer " + token("tenant-admin", tenant, "DCC_TENANT_ADMIN");

        // the tenant comes from the token, never from the body
        mockMvc.perform(post("/api/memberships").header("Authorization", auth)
                        .contentType("application/json")
                        .content("{\"subject\":\"newcomer\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tenantCode").value(tenant));

        // a platform role cannot be laundered through a tenant-scoped grant
        mockMvc.perform(post("/api/role-assignments").header("Authorization", auth)
                        .contentType("application/json")
                        .content("{\"subject\":\"newcomer\",\"role\":\"DCC_PLATFORM_ADMIN\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message", containsString("cannot be granted inside a tenant")));

        mockMvc.perform(post("/api/role-assignments").header("Authorization", auth)
                        .contentType("application/json")
                        .content("{\"subject\":\"newcomer\",\"role\":\"DCC_MAINTAINER\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tenantCode").value(tenant));

        // a grant that belongs to another tenant is invisible here (404, never 403 — no probing)
        var foreignGrant = assignments.create(
                new RoleAssignmentService.CreateCommand("someone", foreign, "DCC_READER"));
        mockMvc.perform(delete("/api/role-assignments/" + foreignGrant.id()).header("Authorization", auth))
                .andExpect(status().isNotFound());

        // a reader cannot grant at all
        mockMvc.perform(post("/api/memberships")
                        .header("Authorization", "Bearer " + token("reader", tenant, "DCC_READER"))
                        .contentType("application/json")
                        .content("{\"subject\":\"x\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void theDelegatedEnvironmentAdministratorMustBeGrantedPerSurface() throws Exception {
        String tenant = newTenant();
        environmentService.create(tenant, new TenantEnvironmentService.CreateCommand("DEV", "Dev", null, 1));
        String auth = "Bearer " + token("tenant-admin", tenant, "DCC_TENANT_ADMIN");

        // tenant-wide would quietly hand out the whole data plane, so it is rejected
        mockMvc.perform(post("/api/role-assignments").header("Authorization", auth)
                        .contentType("application/json")
                        .content("{\"subject\":\"delegate\",\"role\":\"DCC_ENV_ADMIN\",\"scopeType\":\"TENANT\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message", containsString("one environment or application")));
        // omitting the scope defaults to tenant-wide, so it is rejected too
        mockMvc.perform(post("/api/role-assignments").header("Authorization", auth)
                        .contentType("application/json")
                        .content("{\"subject\":\"delegate\",\"role\":\"DCC_ENV_ADMIN\"}"))
                .andExpect(status().isUnprocessableContent());

        // one environment is the intended shape
        mockMvc.perform(post("/api/role-assignments").header("Authorization", auth)
                        .contentType("application/json")
                        .content("{\"subject\":\"delegate\",\"role\":\"DCC_ENV_ADMIN\","
                                + "\"scopeType\":\"ENVIRONMENT\",\"scopeCode\":\"DEV\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("DCC_ENV_ADMIN"))
                .andExpect(jsonPath("$.scopeCode").value("DEV"));
    }

    @Test
    void fourEyesRejectsApprovingYourOwnSubmission() throws Exception {
        String tenant = newTenant();
        Long environmentId = environmentService.create(tenant,
                new TenantEnvironmentService.CreateCommand("DEV", "Development", null, 1)).id();
        Long applicationId = applicationService.create(tenant,
                new TenantApplicationService.CreateCommand("core", "Core", null)).id();

        String alice = "Bearer " + token("alice", tenant, "DCC_MAINTAINER", "DCC_APPROVER");
        String bob = "Bearer " + token("bob", tenant, "DCC_APPROVER");

        // alice authors and submits: the version author is bound from her token
        String created = mockMvc.perform(post("/api/standards")
                        .param("environmentId", String.valueOf(environmentId))
                        .param("applicationId", String.valueOf(applicationId))
                        .header("Authorization", alice)
                        .contentType("application/json")
                        .content(STANDARD_BODY))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        long standardId = ((Number) JsonPath.parse(created).read("$.id")).longValue();

        mockMvc.perform(post("/api/standards/" + standardId + "/status")
                        .param("target", "PENDING_REVIEW").header("Authorization", alice))
                .andExpect(status().isOk());

        // she cannot publish her own change…
        mockMvc.perform(post("/api/standards/" + standardId + "/status")
                        .param("target", "PUBLISHED").header("Authorization", alice))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("four-eyes")));

        // …a different approver can
        mockMvc.perform(post("/api/standards/" + standardId + "/status")
                        .param("target", "PUBLISHED").header("Authorization", bob))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
    }
}
