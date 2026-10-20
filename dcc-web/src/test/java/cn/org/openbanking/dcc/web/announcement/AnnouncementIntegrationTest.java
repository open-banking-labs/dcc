package cn.org.openbanking.dcc.web.announcement;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import cn.org.openbanking.dcc.AbstractIntegrationTest;
import cn.org.openbanking.dcc.application.announcement.AnnouncementService;
import cn.org.openbanking.dcc.application.announcement.AnnouncementService.CreateCommand;
import cn.org.openbanking.dcc.application.tenant.TenantService;
import cn.org.openbanking.dcc.core.announcement.AnnouncementLevel;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MockMvc;

/**
 * System announcements end to end.
 *
 * <p>The announcements table is the one place in this schema where a row may belong to no tenant
 * and must still be readable by everyone, so the isolation is worth pinning down:
 * <ul>
 *   <li>a tenant sees platform announcements <em>and</em> its own, never a sibling's;</li>
 *   <li>the two write scopes are disjoint — a tenant endpoint cannot reach a platform row and the
 *       platform endpoint cannot reach a tenant row (404, not 403).</li>
 * </ul>
 */
@AutoConfigureMockMvc
class AnnouncementIntegrationTest extends AbstractIntegrationTest {

    private static final String SECRET = "dev-only-change-me-please-use-32-bytes";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TenantService tenantService;
    @Autowired
    private AnnouncementService announcementService;

    private String newTenant() {
        String tenant = "t-" + UUID.randomUUID();
        tenantService.create(new TenantService.CreateCommand(tenant, "Tenant " + tenant, null));
        return tenant;
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

    private Long announce(String tenantId, AnnouncementLevel level, String title, Instant startsAt, Instant endsAt) {
        return announcementService.create(tenantId, new CreateCommand(level, title, "body of " + title, startsAt,
                endsAt)).id();
    }

    private static String body(AnnouncementLevel level, String title, String startsAt, String endsAt,
            String enabled) {
        return """
                {"level":"%s","title":"%s","body":"body of %s","startsAt":%s,"endsAt":%s,"enabled":%s}
                """.formatted(level, title, title, startsAt, endsAt, enabled);
    }

    @Test
    void theBannerIsPlatformPlusOwnTenant() throws Exception {
        String tenantA = newTenant();
        String tenantB = newTenant();
        announce(null, AnnouncementLevel.MAINTENANCE, "平台维护窗口", null, null);
        announce(tenantA, AnnouncementLevel.INFO, "A 的内部通知", null, null);

        mockMvc.perform(get("/api/announcements")
                .header("Authorization", "Bearer " + token(tenantA, "DCC_READER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].title", hasItem("平台维护窗口")))
                .andExpect(jsonPath("$[*].title", hasItem("A 的内部通知")))
                // urgent first: the maintenance window outranks the notice
                .andExpect(jsonPath("$[0].level").value("MAINTENANCE"));

        // the sibling tenant sees the platform notice only, never tenant A's
        mockMvc.perform(get("/api/announcements")
                .header("Authorization", "Bearer " + token(tenantB, "DCC_READER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].title", not(hasItem("A 的内部通知"))));
    }

    @Test
    void theWindowAndTheEnabledFlagHideFromTheBannerButNotFromManagement() throws Exception {
        String tenant = newTenant();
        Instant now = Instant.now();
        announce(tenant, AnnouncementLevel.INFO, "已到期", null, now.minus(1, ChronoUnit.HOURS));
        announce(tenant, AnnouncementLevel.INFO, "未开始", now.plus(1, ChronoUnit.HOURS), null);
        announce(tenant, AnnouncementLevel.INFO, "生效中", null, null);
        Long disabled = announce(tenant, AnnouncementLevel.INFO, "已停用", null, null);
        announcementService.update(tenant, disabled, new AnnouncementService.UpdateCommand(
                AnnouncementLevel.INFO, "已停用", "body of 已停用", null, null, false));

        mockMvc.perform(get("/api/announcements")
                .header("Authorization", "Bearer " + token(tenant, "DCC_READER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].title", hasItem("生效中")))
                .andExpect(jsonPath("$[*].title", not(hasItem("已到期"))))
                .andExpect(jsonPath("$[*].title", not(hasItem("未开始"))))
                .andExpect(jsonPath("$[*].title", not(hasItem("已停用"))));

        // management shows everything in scope, so a notice can be switched back on
        mockMvc.perform(get("/api/announcements/managed")
                .header("Authorization", "Bearer " + token(tenant, "DCC_TENANT_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4));
    }

    @Test
    void aTenantAdministratorPublishesIntoItsOwnTenantOnly() throws Exception {
        String tenant = newTenant();

        mockMvc.perform(post("/api/announcements")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(AnnouncementLevel.WARNING, "周五停机", "null", "null", "null"))
                .header("Authorization", "Bearer " + token(tenant, "DCC_TENANT_ADMIN")))
                .andExpect(status().isCreated())
                // the owning tenant comes from the token, never from the request body
                .andExpect(jsonPath("$.tenantId").value(tenant))
                .andExpect(jsonPath("$.title").value("周五停机"))
                .andExpect(jsonPath("$.enabled").value(true));
    }

    @Test
    void aTenantEndpointCannotReachAPlatformAnnouncement() throws Exception {
        String tenant = newTenant();
        Long platform = announce(null, AnnouncementLevel.INFO, "平台公告", null, null);

        mockMvc.perform(put("/api/announcements/" + platform)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(AnnouncementLevel.INFO, "改掉", "null", "null", "null"))
                .header("Authorization", "Bearer " + token(tenant, "DCC_TENANT_ADMIN")))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/announcements/" + platform)
                .header("Authorization", "Bearer " + token(tenant, "DCC_TENANT_ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test
    void thePlatformEndpointCannotReachATenantAnnouncement() throws Exception {
        String tenant = newTenant();
        Long own = announce(tenant, AnnouncementLevel.INFO, "租户公告", null, null);

        mockMvc.perform(put("/api/platform/announcements/" + own)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(AnnouncementLevel.INFO, "改掉", "null", "null", "null"))
                .header("Authorization", "Bearer " + token(tenant, "DCC_PLATFORM_ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test
    void aPlatformAdministratorPublishesPlatformWide() throws Exception {
        String tenant = newTenant();

        mockMvc.perform(post("/api/platform/announcements")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(AnnouncementLevel.MAINTENANCE, "全网维护", "null", "null", "null"))
                .header("Authorization", "Bearer " + token(tenant, "DCC_PLATFORM_ADMIN")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tenantId").value(nullValue()));

        mockMvc.perform(get("/api/platform/announcements")
                .header("Authorization", "Bearer " + token(tenant, "DCC_PLATFORM_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].title", hasItem("全网维护")));
    }

    @Test
    void readingNeedsNoPermissionPointButPublishingDoes() throws Exception {
        String tenant = newTenant();
        announce(null, AnnouncementLevel.INFO, "普通通知", null, null);

        // a reader sees the banner...
        mockMvc.perform(get("/api/announcements")
                .header("Authorization", "Bearer " + token(tenant, "DCC_READER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].title", hasItem("普通通知")));
        // ...but neither the management list nor a publish
        mockMvc.perform(get("/api/announcements/managed")
                .header("Authorization", "Bearer " + token(tenant, "DCC_READER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/announcements")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(AnnouncementLevel.INFO, "不该发布", "null", "null", "null"))
                .header("Authorization", "Bearer " + token(tenant, "DCC_READER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void aTenantAdministratorCannotPublishPlatformWide() throws Exception {
        String tenant = newTenant();

        mockMvc.perform(post("/api/platform/announcements")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(AnnouncementLevel.INFO, "越权", "null", "null", "null"))
                .header("Authorization", "Bearer " + token(tenant, "DCC_TENANT_ADMIN")))
                .andExpect(status().isForbidden());
    }

    @Test
    void aLogicalDeleteStopsTheBanner() throws Exception {
        String tenant = newTenant();
        Long id = announce(tenant, AnnouncementLevel.INFO, "临时通知", null, null);

        mockMvc.perform(delete("/api/announcements/" + id)
                .header("Authorization", "Bearer " + token(tenant, "DCC_TENANT_ADMIN")))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/announcements")
                .header("Authorization", "Bearer " + token(tenant, "DCC_READER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].title", not(hasItem("临时通知"))));
    }

    @Test
    void aWindowThatEndsBeforeItStartsIsRejected() throws Exception {
        String tenant = newTenant();
        Instant now = Instant.now();

        mockMvc.perform(post("/api/announcements")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(AnnouncementLevel.INFO, "时间反了",
                        "\"" + now + "\"", "\"" + now.minus(1, ChronoUnit.HOURS) + "\"", "null"))
                .header("Authorization", "Bearer " + token(tenant, "DCC_TENANT_ADMIN")))
                .andExpect(status().isUnprocessableEntity());
    }
}
