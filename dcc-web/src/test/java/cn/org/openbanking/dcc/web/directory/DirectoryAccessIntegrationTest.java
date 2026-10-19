package cn.org.openbanking.dcc.web.directory;

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
import cn.org.openbanking.dcc.application.tenant.TenantMembershipService;
import cn.org.openbanking.dcc.application.tenant.TenantService;

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
 * End-to-end behaviour of the authorization directory in directory mode: the token is
 * only an identity, membership gates the subject/tenant pair and directory-assigned
 * roles are unioned with the claim roles. Runs with
 * {@code dcc.security.directory-mode=true}, so it owns its Spring context (the
 * default, claim-only behaviour is covered by the other integration tests).
 */
@SpringBootTest(properties = "dcc.security.directory-mode=true")
@AutoConfigureMockMvc
class DirectoryAccessIntegrationTest extends AbstractIntegrationTest {

    private static final String SECRET = "dev-only-change-me-please-use-32-bytes";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TenantService tenantService;
    @Autowired
    private TenantMembershipService memberships;
    @Autowired
    private RoleAssignmentService assignments;

    private String tenantMember;
    private String tenantForeign;
    /** Member of {@link #tenantMember} with a directory-assigned DCC_MAINTAINER role. */
    private String assigned;
    /** Member of {@link #tenantMember} whose authority comes only from claim roles... none. */
    private String authorityless;

    @BeforeEach
    void seedDirectory() {
        tenantMember = "t-" + UUID.randomUUID();
        tenantForeign = "t-" + UUID.randomUUID();
        assigned = "user-" + UUID.randomUUID();
        authorityless = "user-" + UUID.randomUUID();
        tenantService.create(new TenantService.CreateCommand(tenantMember, "member tenant", null));
        tenantService.create(new TenantService.CreateCommand(tenantForeign, "foreign tenant", null));
        memberships.create(new TenantMembershipService.CreateCommand(assigned, tenantMember));
        memberships.create(new TenantMembershipService.CreateCommand(authorityless, tenantMember));
        assignments.create(new RoleAssignmentService.CreateCommand(assigned, tenantMember, "DCC_MAINTAINER"));
    }

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

    @Test
    void membershipGatePassesAndDirectoryRoleGrantsAdmin() throws Exception {
        // claim roles say DCC_READER; the directory assignment supplies DCC_MAINTAINER, the
        // membership is live, so the request reaches the use case (404: no such table)
        mockMvc.perform(delete("/api/tables/999")
                        .header("Authorization", "Bearer " + token(assigned, tenantMember, "DCC_READER")))
                .andExpect(status().isNotFound());
    }

    @Test
    void claimRolesCannotBuyDirectoryMembership() throws Exception {
        // a perfectly valid admin token for a subject the directory never granted
        mockMvc.perform(delete("/api/tables/999")
                        .header("Authorization", "Bearer " + token("stranger", tenantMember, "DCC_PLATFORM_ADMIN")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("not_a_directory_member"));
    }

    @Test
    void membershipIsScopedToTheTenant() throws Exception {
        // the same subject, a tenant of which they are not a member
        mockMvc.perform(delete("/api/tables/999")
                        .header("Authorization", "Bearer " + token(assigned, tenantForeign, "DCC_TENANT_ADMIN")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("not_a_directory_member"));
    }

    @Test
    void memberWithoutGrantableRoleStillNeedsAnAuthority() throws Exception {
        // membership passes the directory gate, but no directory role and only DCC_READER
        mockMvc.perform(delete("/api/tables/999")
                        .header("Authorization", "Bearer " + token(authorityless, tenantMember, "DCC_READER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void myMembershipsAreVisibleToAnyAuthenticatedMemberNotOnlyAdmins() throws Exception {
        // the seeded member holds a tenant role; the endpoint is not admin-guarded
        mockMvc.perform(get("/api/directory/my-memberships")
                        .header("Authorization", "Bearer " + token(assigned, tenantMember, "DCC_READER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tenantCode").value(tenantMember))
                .andExpect(jsonPath("$[0].subject").value(assigned));
    }

    @Test
    void adminCanManageDirectoryRowsAndRevocationCutsAccess() throws Exception {
        // The console admin manages rows through the REST directory group. First, need a
        // membership for the managing subject (created service-side, like a real onboarding
        // would; the bootstrap of the very first directory admin is a deployment-time
        // operation, see docs/configuration.md).
        String admin = "user-" + UUID.randomUUID();
        memberships.create(new TenantMembershipService.CreateCommand(admin, tenantMember));

        String adminAuth = "Bearer " + token(admin, tenantMember, "DCC_PLATFORM_ADMIN");

        // grant a second member through the REST API
        String other = "user-" + UUID.randomUUID();
        mockMvc.perform(post("/api/directory/memberships").header("Authorization", adminAuth)
                        .contentType("application/json")
                        .content("{\"subject\":\"" + other + "\",\"tenantCode\":\"" + tenantMember + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tenantCode").value(tenantMember));

        // duplicate grant -> 409 from the partial unique index (REST-visible)
        mockMvc.perform(post("/api/directory/memberships").header("Authorization", adminAuth)
                        .contentType("application/json")
                        .content("{\"subject\":\"" + other + "\",\"tenantCode\":\"" + tenantMember + "\"}"))
                .andExpect(status().isConflict());

        // the seeded subject's page stays searchable
        mockMvc.perform(get("/api/directory/memberships").header("Authorization", adminAuth)
                        .param("subject", assigned).param("tenant", tenantMember))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        // revoke the seeded subject's membership: their next request is rejected at the gate
        long membershipId = searchMembershipId(adminAuth);
        mockMvc.perform(delete("/api/directory/memberships/" + membershipId).header("Authorization", adminAuth))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete("/api/tables/999")
                        .header("Authorization", "Bearer " + token(assigned, tenantMember, "DCC_READER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("not_a_directory_member"));

        // restoration puts them back into the directory
        mockMvc.perform(post("/api/directory/memberships/" + membershipId + "/restore")
                        .header("Authorization", adminAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").value(assigned));
        mockMvc.perform(delete("/api/tables/999")
                        .header("Authorization", "Bearer " + token(assigned, tenantMember, "DCC_READER")))
                // restored membership but revoked-ish? assignment still live: admin again
                .andExpect(status().isNotFound());
    }

    private Long searchMembershipId(String adminAuth) throws Exception {
        String body = mockMvc.perform(get("/api/directory/memberships").header("Authorization", adminAuth)
                        .param("subject", assigned).param("tenant", tenantMember))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return ((Number) JsonPath.parse(body).read("$.content[0].id")).longValue();
    }
}
