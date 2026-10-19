package cn.org.openbanking.dcc.web.directory;

import static org.hamcrest.Matchers.containsString;
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
import cn.org.openbanking.dcc.application.tenant.TenantMembershipService;
import cn.org.openbanking.dcc.application.tenant.TenantService;

import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jose.jwk.source.ImmutableSecret;

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
 * Dual control on privileged grants (ADR 0005 §D10): a platform/tenant administrator grant is
 * written and audited immediately, but confers nothing until a <em>different</em> subject confirms
 * it — so no single administrator can mint another one.
 */
@SpringBootTest(properties = "dcc.security.directory-mode=true")
@AutoConfigureMockMvc
class DualControlIntegrationTest extends AbstractIntegrationTest {

    private static final String SECRET = "dev-only-change-me-please-use-32-bytes";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TenantService tenantService;
    @Autowired
    private TenantMembershipService memberships;

    private String token(String subject, String tenant, String... roles) {
        SecretKey key = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(subject)
                .claim("tenant", tenant)
                .claim("roles", List.of(roles))
                .build();
        return "Bearer " + encoder
                .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    @Test
    void aPrivilegedGrantConfersNothingUntilASecondSubjectConfirmsIt() throws Exception {
        String tenant = "t-" + UUID.randomUUID();
        tenantService.create(new TenantService.CreateCommand(tenant, "Dual control", null));
        String newcomer = "newcomer-" + UUID.randomUUID();
        // directory mode gates on a live membership, so the two administrators must be members too
        memberships.create(new TenantMembershipService.CreateCommand(newcomer, tenant));
        memberships.create(new TenantMembershipService.CreateCommand("admin-a", tenant));
        memberships.create(new TenantMembershipService.CreateCommand("admin-b", tenant));

        String adminA = token("admin-a", tenant, "DCC_PLATFORM_ADMIN");
        String adminB = token("admin-b", tenant, "DCC_PLATFORM_ADMIN");
        String newcomerAuth = token(newcomer, tenant, "DCC_READER");

        // A grants tenant administration to the newcomer through the platform (bootstrap) surface
        String granted = mockMvc.perform(post("/api/directory/role-assignments")
                        .header("Authorization", adminA)
                        .contentType("application/json")
                        .content("{\"subject\":\"" + newcomer + "\",\"tenantCode\":\"" + tenant
                                + "\",\"role\":\"DCC_TENANT_ADMIN\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.confirmationRequired").value(true))
                .andExpect(jsonPath("$.grantedBy").value("admin-a"))
                .andExpect(jsonPath("$.confirmedBy").doesNotExist())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        long assignmentId = ((Number) JsonPath.parse(granted).read("$.id")).longValue();

        // pending: the directory ignores it, so the newcomer is still a plain reader
        mockMvc.perform(get("/api/audit").header("Authorization", newcomerAuth))
                .andExpect(status().isForbidden());

        // the granting subject cannot confirm its own grant
        mockMvc.perform(post("/api/directory/role-assignments/" + assignmentId + "/confirm")
                        .header("Authorization", adminA))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("dual control")));

        // a second administrator can
        mockMvc.perform(post("/api/directory/role-assignments/" + assignmentId + "/confirm")
                        .header("Authorization", adminB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmedBy").value("admin-b"));

        // now, and only now, the grant confers the tenant administrator's authorities
        mockMvc.perform(get("/api/audit").header("Authorization", newcomerAuth))
                .andExpect(status().isOk());
    }

    @Test
    void ordinaryRolesDoNotNeedConfirmation() throws Exception {
        String tenant = "t-" + UUID.randomUUID();
        tenantService.create(new TenantService.CreateCommand(tenant, "No dual control", null));
        memberships.create(new TenantMembershipService.CreateCommand("admin-a2", tenant));
        String adminA = token("admin-a2", tenant, "DCC_PLATFORM_ADMIN");

        String created = mockMvc.perform(post("/api/directory/role-assignments")
                        .header("Authorization", adminA)
                        .contentType("application/json")
                        .content("{\"subject\":\"plain-maintainer\",\"tenantCode\":\"" + tenant
                                + "\",\"role\":\"DCC_MAINTAINER\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.confirmationRequired").value(false))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        // confirming a grant that needs no confirmation is a caller error, not a silent no-op
        long assignmentId = ((Number) JsonPath.parse(created).read("$.id")).longValue();
        mockMvc.perform(post("/api/directory/role-assignments/" + assignmentId + "/confirm")
                        .header("Authorization", adminA))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message", containsString("does not require confirmation")));
    }
}
