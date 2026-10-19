package cn.org.openbanking.dcc.web.security;

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
import cn.org.openbanking.dcc.application.standard.DataStandardService;
import cn.org.openbanking.dcc.application.tenant.TenantApplicationService;
import cn.org.openbanking.dcc.application.tenant.TenantEnvironmentService;
import cn.org.openbanking.dcc.application.tenant.TenantMembershipService;
import cn.org.openbanking.dcc.application.tenant.TenantService;
import cn.org.openbanking.dcc.core.authorization.RolePermission;
import cn.org.openbanking.dcc.core.authorization.repository.RolePermissionRepository;
import cn.org.openbanking.dcc.core.standard.StandardContent;
import cn.org.openbanking.dcc.core.standard.StandardStatus;

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
 * Database-backed role → permission resolver (ADR 0005 §D8): with
 * {@code dcc.security.resolver-mode=database} the table is authoritative — the configuration
 * bundles confer nothing at all.
 */
@SpringBootTest(properties = {"dcc.security.directory-mode=true", "dcc.security.resolver-mode=database"})
@AutoConfigureMockMvc
class DatabaseRoleResolverIntegrationTest extends AbstractIntegrationTest {

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
    private DataStandardService standardService;
    @Autowired
    private RolePermissionRepository rolePermissions;

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
    void theTableIsAuthoritativeAndTheConfigBundlesDoNotApply() throws Exception {
        String tenant = "t-" + UUID.randomUUID();
        tenantService.create(new TenantService.CreateCommand(tenant, "DB resolver", null));
        Long environmentId = environmentService.create(tenant,
                new TenantEnvironmentService.CreateCommand("UAT", "Acceptance", null, 1)).id();
        Long applicationId = applicationService.create(tenant,
                new TenantApplicationService.CreateCommand("core", "Core", null)).id();

        String subject = "db-approver-" + UUID.randomUUID();
        memberships.create(new TenantMembershipService.CreateCommand(subject, tenant));
        rolePermissions.save(new RolePermission("OPS_APPROVER", "artifact.transition"));
        rolePermissions.save(new RolePermission("OPS_APPROVER", "artifact.read"));

        // the DB mapping is what counts
        mockMvc.perform(get("/api/me").header("Authorization", token(subject, tenant, "OPS_APPROVER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions", hasItem("artifact.transition")))
                .andExpect(jsonPath("$.permissions", not(hasItem("artifact.write"))));

        // and a removed legacy role name with no rows confers nothing: no config fallback in this mode
        mockMvc.perform(get("/api/me").header("Authorization", token(subject, tenant, "DCC_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions", not(hasItem("artifact.write"))))
                .andExpect(jsonPath("$.permissions", not(hasItem("tenant.admit"))));

        // end to end: the DB-mapped role can approve
        var created = standardService.create(tenant, environmentId, applicationId,
                new DataStandardService.CreateCommand("db-mapped", null,
                        new StandardContent("账号", null, "VARCHAR", 32, null, true, null, null, null, "1")));
        standardService.transition(tenant, created.id(), StandardStatus.PENDING_REVIEW);

        mockMvc.perform(post("/api/standards/" + created.id() + "/status")
                        .param("target", "PUBLISHED")
                        .header("Authorization", token(subject, tenant, "OPS_APPROVER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
    }

    @Test
    void aRoleWithoutMappingsConfersNothing() throws Exception {
        String tenant = "t-" + UUID.randomUUID();
        tenantService.create(new TenantService.CreateCommand(tenant, "No mappings", null));
        String subject = "unmapped-" + UUID.randomUUID();
        memberships.create(new TenantMembershipService.CreateCommand(subject, tenant));

        mockMvc.perform(get("/api/me").header("Authorization", token(subject, tenant, "SOME_UNMAPPED_ROLE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions").isEmpty());
    }
}
