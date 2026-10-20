package cn.org.openbanking.dcc.web.dashboard;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import cn.org.openbanking.dcc.application.table.TableStructureService;
import cn.org.openbanking.dcc.application.tenant.TenantApplicationService;
import cn.org.openbanking.dcc.application.tenant.TenantEnvironmentService;
import cn.org.openbanking.dcc.application.tenant.TenantService;
import cn.org.openbanking.dcc.core.interfaceapi.repository.InterfaceDefinitionRepository;
import cn.org.openbanking.dcc.core.migration.ArtifactType;
import cn.org.openbanking.dcc.core.standard.StandardContent;
import cn.org.openbanking.dcc.core.standard.StandardStatus;
import cn.org.openbanking.dcc.core.table.content.TableColumn;
import cn.org.openbanking.dcc.core.table.content.TableContent;

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
 * The workbench dashboard end to end: real HTTP, real bearer tokens, real PostgreSQL.
 *
 * <p>Two properties are worth a test rather than a comment:
 * <ul>
 *   <li><b>Counts follow the requested scope.</b> The console's environment switcher sends
 *       {@code environmentId}; a dashboard that ignored it would quietly show the whole tenant.</li>
 *   <li><b>A section the caller cannot read is {@code null}, not zero.</b> Zero and "not allowed"
 *       must never render the same card — that is the defect this endpoint exists to avoid.</li>
 * </ul>
 */
@AutoConfigureMockMvc
class DashboardIntegrationTest extends AbstractIntegrationTest {

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
    private DataStandardService standardService;
    @Autowired
    private TableStructureService tableService;
    @Autowired
    private MigrationService migrationService;

    /** One tenant with two environments, so a migration order has a distinct target. */
    private record Scope(String tenant, Long devEnvironmentId, Long testEnvironmentId, Long applicationId) {
    }

    private Scope newScope() {
        String tenant = "t-" + UUID.randomUUID();
        tenantService.create(new TenantService.CreateCommand(tenant, "Tenant " + tenant, null));
        var dev = environmentService.create(tenant,
                new TenantEnvironmentService.CreateCommand("DEV", "Development", null, 1));
        var test = environmentService.create(tenant,
                new TenantEnvironmentService.CreateCommand("TEST", "Test", null, 2));
        var application = applicationService.create(tenant,
                new TenantApplicationService.CreateCommand("core", "Core", null));
        return new Scope(tenant, dev.id(), test.id(), application.id());
    }

    private String token(String tenant, String... roles) {
        SecretKey key = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject("tester")
                .claim("tenant", tenant)
                .claim("roles", List.of(roles))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private static StandardContent standardContent() {
        return new StandardContent("账号", "account number", "VARCHAR", 32, null, true, null, null, null, "123456");
    }

    private Long newStandard(Scope scope, Long environmentId, String code) {
        return standardService.create(scope.tenant(), environmentId, scope.applicationId(),
                new DataStandardService.CreateCommand(code, "account", standardContent())).id();
    }

    @Test
    void countsAndStatusBreakdownFollowTheRequestedScope() throws Exception {
        Scope scope = newScope();
        Long draft = newStandard(scope, scope.devEnvironmentId(), "acctno");
        newStandard(scope, scope.devEnvironmentId(), "custno");
        Long review = newStandard(scope, scope.devEnvironmentId(), "cardno");
        standardService.transition(scope.tenant(), review, StandardStatus.PENDING_REVIEW);
        // a table in the same scope, so more than one artifact kind is exercised
        tableService.create(scope.tenant(), scope.devEnvironmentId(), scope.applicationId(),
                new TableStructureService.CreateCommand("account",
                        new TableContent("Account", "账户表", "acct_no",
                                List.of(new TableColumn("acct_no", draft, "acctno", null, null, null, false, true,
                                        null, 1, "账号")),
                                List.of())));
        // one order leaving this environment, i.e. targeting TEST
        migrationService.create(scope.tenant(), new MigrationService.CreateCommand(ArtifactType.DATA_STANDARD,
                draft, scope.devEnvironmentId(), scope.testEnvironmentId(), "promote"));

        mockMvc.perform(get("/api/dashboard/summary")
                .param("environmentId", String.valueOf(scope.devEnvironmentId()))
                .param("applicationId", String.valueOf(scope.applicationId()))
                .header("Authorization", "Bearer " + token(scope.tenant(), "DCC_TENANT_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(scope.tenant()))
                .andExpect(jsonPath("$.artifacts.dataStandard.total").value(3))
                .andExpect(jsonPath("$.artifacts.dataStandard.pendingReview").value(1))
                .andExpect(jsonPath("$.artifacts.tableStructure.total").value(1))
                .andExpect(jsonPath("$.artifacts.tableStructure.pendingReview").value(0))
                .andExpect(jsonPath("$.artifacts.interfaces.total").value(0))
                .andExpect(jsonPath("$.artifacts.interfaceTemplates.total").value(0))
                // the order targets TEST, so nothing is queued *for* this environment
                .andExpect(jsonPath("$.migrations.pendingSubmission").value(0))
                // a tenant administrator runs no platform plane
                .andExpect(jsonPath("$.tenants").value(nullValue()));

        // the same switcher read as a migration target: TEST is where that order lands
        mockMvc.perform(get("/api/dashboard/summary")
                .param("environmentId", String.valueOf(scope.testEnvironmentId()))
                .header("Authorization", "Bearer " + token(scope.tenant(), "DCC_TENANT_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.migrations.pendingSubmission").value(1))
                .andExpect(jsonPath("$.migrations.pendingApproval").value(0))
                .andExpect(jsonPath("$.recentMigrations[0].artifactType").value("DATA_STANDARD"))
                .andExpect(jsonPath("$.recentMigrations[0].artifactCode").value("acctno"))
                .andExpect(jsonPath("$.recentMigrations[0].status").value("PENDING_SUBMISSION"));
    }

    @Test
    void scopeFilterNarrowsTheCounts() throws Exception {
        Scope scope = newScope();
        newStandard(scope, scope.devEnvironmentId(), "acctno");
        newStandard(scope, scope.testEnvironmentId(), "custno");

        mockMvc.perform(get("/api/dashboard/summary")
                .header("Authorization", "Bearer " + token(scope.tenant(), "DCC_TENANT_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.artifacts.dataStandard.total").value(2));

        mockMvc.perform(get("/api/dashboard/summary")
                .param("environmentId", String.valueOf(scope.testEnvironmentId()))
                .header("Authorization", "Bearer " + token(scope.tenant(), "DCC_TENANT_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.artifacts.dataStandard.total").value(1))
                .andExpect(jsonPath("$.artifacts.dataStandard.pendingReview").value(0));
    }

    @Test
    void recentMigrationsAreNewestFirstAndCapped() throws Exception {
        Scope scope = newScope();
        Long first = newStandard(scope, scope.devEnvironmentId(), "acctno");
        Long second = newStandard(scope, scope.devEnvironmentId(), "custno");
        var oldest = migrationService.create(scope.tenant(), new MigrationService.CreateCommand(
                ArtifactType.DATA_STANDARD, first, scope.devEnvironmentId(), scope.testEnvironmentId(), null));
        var newest = migrationService.create(scope.tenant(), new MigrationService.CreateCommand(
                ArtifactType.DATA_STANDARD, second, scope.devEnvironmentId(), scope.testEnvironmentId(), null));

        mockMvc.perform(get("/api/dashboard/summary")
                .header("Authorization", "Bearer " + token(scope.tenant(), "DCC_TENANT_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recentMigrations.length()").value(2))
                // id desc: the later order comes first, whatever the insertion order was
                .andExpect(jsonPath("$.recentMigrations[0].id").value(newest.id().intValue()))
                .andExpect(jsonPath("$.recentMigrations[1].id").value(oldest.id().intValue()));
    }

    @Test
    void aReaderGetsArtifactsButNoMigrationOrAuditCards() throws Exception {
        Scope scope = newScope();
        newStandard(scope, scope.devEnvironmentId(), "acctno");

        mockMvc.perform(get("/api/dashboard/summary")
                .header("Authorization", "Bearer " + token(scope.tenant(), "DCC_READER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.artifacts.dataStandard.total").value(1))
                // DCC_READER carries neither migration.write nor migration.approve
                .andExpect(jsonPath("$.migrations").value(nullValue()))
                .andExpect(jsonPath("$.recentMigrations").value(nullValue()))
                .andExpect(jsonPath("$.audit").value(nullValue()));
    }

    @Test
    void aPlatformAdministratorGetsTheRegistryAndPlatformAuditOnly() throws Exception {
        Scope scope = newScope();
        newStandard(scope, scope.devEnvironmentId(), "acctno");

        mockMvc.perform(get("/api/dashboard/summary")
                .header("Authorization", "Bearer " + token(scope.tenant(), "DCC_PLATFORM_ADMIN")))
                .andExpect(status().isOk())
                // DCC_PLATFORM_ADMIN deliberately carries no artifact.read (ADR 0005 §D9)
                .andExpect(jsonPath("$.artifacts").value(nullValue()))
                .andExpect(jsonPath("$.migrations").value(nullValue()))
                .andExpect(jsonPath("$.tenants.total").isNumber())
                .andExpect(jsonPath("$.audit.last24h").isNumber());
    }

    @Test
    void anUnauthenticatedCallerIsRejected() throws Exception {
        mockMvc.perform(get("/api/dashboard/summary")).andExpect(status().isForbidden());
    }
}
