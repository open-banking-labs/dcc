package cn.org.openbanking.dcc.web.standard;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import cn.org.openbanking.dcc.AbstractIntegrationTest;
import cn.org.openbanking.dcc.application.standard.DataStandardService;
import cn.org.openbanking.dcc.application.standard.DataStandardService.CreateCommand;
import cn.org.openbanking.dcc.application.tenant.TenantApplicationService;
import cn.org.openbanking.dcc.application.tenant.TenantEnvironmentService;
import cn.org.openbanking.dcc.application.tenant.TenantService;
import cn.org.openbanking.dcc.core.standard.StandardContent;
import cn.org.openbanking.dcc.core.standard.export.repository.ExportJobRepository;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** HTTP wiring for the console's paged search, summaries, template/import and export routes. */
@AutoConfigureMockMvc
@TestPropertySource(properties = "dcc.export.worker-enabled=false")
class DataStandardConsoleEndpointIntegrationTest extends AbstractIntegrationTest {

    private static final String SECRET = "dev-only-change-me-please-use-32-bytes";

    private static final String EXCEL_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

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
    private ExportJobRepository exportJobs;

    private final Set<String> tenants = new HashSet<>();

    private record Scope(String tenant, Long environmentId, Long applicationId) {
    }

    @AfterEach
    void removeExportJobs() {
        // A PENDING job would otherwise be claimed by another test's worker call.
        exportJobs.findAll().stream()
                .filter(job -> tenants.contains(job.getTenantId()))
                .forEach(exportJobs::delete);
        tenants.clear();
    }

    private Scope newScope() {
        String tenant = "t-" + UUID.randomUUID();
        tenants.add(tenant);
        tenantService.create(new TenantService.CreateCommand(tenant, "Tenant " + tenant, null));
        var environment = environmentService.create(tenant,
                new TenantEnvironmentService.CreateCommand("DEV", "Development", null, 1));
        var application = applicationService.create(tenant,
                new TenantApplicationService.CreateCommand("core", "Core", null));
        return new Scope(tenant, environment.id(), application.id());
    }

    private String token(Scope scope, String... roles) {
        SecretKey key = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject("console-user")
                .claim("tenant", scope.tenant())
                .claim("roles", List.of(roles))
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    @Test
    void pagedSearchAndSummariesResolveBeforeTheIdRoute() throws Exception {
        Scope scope = newScope();
        var standard = standardService.create(scope.tenant(), scope.environmentId(), scope.applicationId(),
                new CreateCommand("ACCT_NO", "account", content("账号")));
        String auth = "Bearer " + token(scope, "DCC_READER");

        mockMvc.perform(get("/api/standards/page")
                        .param("environmentId", String.valueOf(scope.environmentId()))
                        .param("applicationId", String.valueOf(scope.applicationId()))
                        .param("keyword", "acct")
                        .header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].code").value("ACCT_NO"));

        mockMvc.perform(get("/api/standards/summaries")
                        .param("ids", String.valueOf(standard.id()))
                        .header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("ACCT_NO"));
    }

    @Test
    void importTemplateIsServedAsAnXlsxAttachment() throws Exception {
        Scope scope = newScope();
        mockMvc.perform(get("/api/standards/import-template")
                        .header("Authorization", "Bearer " + token(scope, "DCC_MAINTAINER")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", EXCEL_CONTENT_TYPE))
                .andExpect(header().string("Content-Disposition",
                        containsString("standards-import-template.xlsx")));
    }

    @Test
    void excelImportAcceptsMultipartUpload() throws Exception {
        Scope scope = newScope();
        MockMultipartFile file = new MockMultipartFile("file", "standards.xlsx",
                EXCEL_CONTENT_TYPE, workbook());

        mockMvc.perform(multipart("/api/standards/import-excel")
                        .file(file)
                        .param("environmentId", String.valueOf(scope.environmentId()))
                        .param("applicationId", String.valueOf(scope.applicationId()))
                        .header("Authorization", "Bearer " + token(scope, "DCC_MAINTAINER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(1))
                .andExpect(jsonPath("$.errors").isEmpty());
    }

    @Test
    void exportRequestReturnsAcceptedJob() throws Exception {
        Scope scope = newScope();
        mockMvc.perform(post("/api/standards/exports")
                        .contentType("application/json")
                        .content("{\"environmentId\":" + scope.environmentId()
                                + ",\"applicationId\":" + scope.applicationId() + "}")
                        .header("Authorization", "Bearer " + token(scope, "DCC_READER")))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    private static byte[] workbook() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("数标");
            String[] headers = {"编码*", "分类", "名称*", "数据类型*", "长度", "精度",
                    "必填(是/否)", "默认值", "枚举值", "正则", "示例值", "描述"};
            Row header = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }
            Row row = sheet.createRow(1);
            row.createCell(0).setCellValue("IMPORTED");
            row.createCell(1).setCellValue("account");
            row.createCell(2).setCellValue("导入数标");
            row.createCell(3).setCellValue("VARCHAR");
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private static StandardContent content(String name) {
        return new StandardContent(name, null, "VARCHAR", 32, null, false, null, null, null, null);
    }
}
