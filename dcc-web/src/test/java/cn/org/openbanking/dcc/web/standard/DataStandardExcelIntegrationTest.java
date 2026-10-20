package cn.org.openbanking.dcc.web.standard;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import cn.org.openbanking.dcc.AbstractIntegrationTest;
import cn.org.openbanking.dcc.application.standard.DataStandardService;
import cn.org.openbanking.dcc.application.standard.DataStandardService.CreateCommand;
import cn.org.openbanking.dcc.application.standard.DataStandardService.ImportResult;
import cn.org.openbanking.dcc.application.standard.excel.StandardExcelService;
import cn.org.openbanking.dcc.application.standard.export.ExportStorage;
import cn.org.openbanking.dcc.application.standard.export.StandardExportService;
import cn.org.openbanking.dcc.application.standard.export.StandardExportService.CreateExportCommand;
import cn.org.openbanking.dcc.application.tenant.TenantApplicationService;
import cn.org.openbanking.dcc.application.tenant.TenantEnvironmentService;
import cn.org.openbanking.dcc.application.tenant.TenantService;
import cn.org.openbanking.dcc.core.standard.StandardContent;
import cn.org.openbanking.dcc.core.standard.export.repository.ExportJobRepository;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;

/** Excel template/import parsing and the asynchronous export job, with in-memory storage. */
@TestPropertySource(properties = "dcc.export.worker-enabled=false")
class DataStandardExcelIntegrationTest extends AbstractIntegrationTest {

    @TestConfiguration
    static class RecordingStorageConfiguration {
        @Bean
        @Primary
        RecordingExportStorage recordingExportStorage() {
            return new RecordingExportStorage();
        }
    }

    static class RecordingExportStorage implements ExportStorage {

        private final ConcurrentHashMap<String, byte[]> objects = new ConcurrentHashMap<>();

        @Override
        public void put(String key, Path file) {
            try {
                objects.put(key, Files.readAllBytes(file));
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        }

        @Override
        public String presign(String key, String fileName) {
            return "https://example.test/" + key;
        }

        @Override
        public void delete(String key) {
            objects.remove(key);
        }

        byte[] single() {
            return objects.values().iterator().next();
        }
    }

    @Autowired
    private TenantService tenantService;
    @Autowired
    private TenantEnvironmentService environmentService;
    @Autowired
    private TenantApplicationService applicationService;
    @Autowired
    private DataStandardService standardService;
    @Autowired
    private StandardExcelService excelService;
    @Autowired
    private StandardExportService exportService;
    @Autowired
    private ExportJobRepository exportJobs;
    @Autowired
    private RecordingExportStorage storage;

    private record Scope(String tenant, Long environmentId, Long applicationId) {
    }

    private Scope scope;

    @BeforeEach
    void setUp() {
        storage.objects.clear();
        String tenant = "t-" + UUID.randomUUID();
        tenantService.create(new TenantService.CreateCommand(tenant, "Tenant " + tenant, null));
        var environment = environmentService.create(tenant,
                new TenantEnvironmentService.CreateCommand("DEV", "Development", null, 1));
        var application = applicationService.create(tenant,
                new TenantApplicationService.CreateCommand("core", "Core", null));
        scope = new Scope(tenant, environment.id(), application.id());
    }

    @AfterEach
    void removeExportJobs() {
        if (scope == null) return;
        exportJobs.findAll().stream()
                .filter(job -> scope.tenant().equals(job.getTenantId()))
                .forEach(exportJobs::delete);
    }

    @Test
    void templateCarriesTheImportHeadersAndInstructions() throws IOException {
        byte[] bytes = excelService.importTemplate();
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet data = workbook.getSheetAt(0);
            Row header = data.getRow(0);
            assertThat(header.getCell(0).getStringCellValue()).isEqualTo("编码*");
            assertThat(header.getCell(2).getStringCellValue()).isEqualTo("名称*");
            assertThat(header.getCell(3).getStringCellValue()).isEqualTo("数据类型*");
            assertThat(workbook.getSheet("填写说明")).isNotNull();
            assertThat(data.getLastRowNum()).isZero();
        }
    }

    @Test
    void importCreatesAndUpdatesByCode() throws IOException {
        var existing = standardService.create(scope.tenant(), scope.environmentId(), scope.applicationId(),
                new CreateCommand("EXISTING", "old", content("旧名称")));

        byte[] workbook = workbook(new String[][]{
                {"NEW_ONE", "account", "新数标", "VARCHAR", "32", "", "是", "", "", "", "", "新增"},
                {"EXISTING", "account", "更新后的名称", "INT", "", "", "否", "", "", "", "", ""}
        });
        ImportResult result = excelService.importXlsx(scope.tenant(), scope.environmentId(),
                scope.applicationId(), "standards.xlsx", new ByteArrayInputStream(workbook));

        assertThat(result.errors()).isEmpty();
        assertThat(result.created()).isEqualTo(1);
        assertThat(result.updated()).isEqualTo(1);
        assertThat(standardService.get(scope.tenant(), existing.id()).content().name())
                .isEqualTo("更新后的名称");
        assertThat(standardService.query(scope.tenant(), scope.environmentId(), scope.applicationId(),
                "NEW_ONE", null, null, 0, 20).getContent()).hasSize(1);
    }

    @Test
    void oneInvalidRowRejectsTheWholeImport() throws IOException {
        byte[] workbook = workbook(new String[][]{
                {"NEW_TWO", "", "有效行", "VARCHAR", "", "", "", "", "", "", "", ""},
                {"BROKEN", "", "", "VARCHAR", "", "", "", "", "", "", "", ""}
        });
        ImportResult result = excelService.importXlsx(scope.tenant(), scope.environmentId(),
                scope.applicationId(), "standards.xlsx", new ByteArrayInputStream(workbook));

        assertThat(result.created()).isZero();
        assertThat(result.updated()).isZero();
        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().get(0).row()).isEqualTo(3);
        assertThat(standardService.query(scope.tenant(), scope.environmentId(), scope.applicationId(),
                "NEW_TWO", null, null, 0, 20).getContent()).isEmpty();
    }

    @Test
    void exportJobStreamsTheWorkbookAndExposesALink() throws IOException {
        standardService.create(scope.tenant(), scope.environmentId(), scope.applicationId(),
                new CreateCommand("EXPORT_A", "account", content("导出 A")));
        standardService.create(scope.tenant(), scope.environmentId(), scope.applicationId(),
                new CreateCommand("EXPORT_B", "customer", content("导出 B")));

        var queued = exportService.create(scope.tenant(),
                new CreateExportCommand(scope.environmentId(), scope.applicationId(), null, null, null, null));
        assertThat(queued.status()).isEqualTo("PENDING");

        exportService.processNextJob();

        var ready = exportService.get(scope.tenant(), queued.id());
        assertThat(ready.status()).isEqualTo("READY");
        assertThat(ready.rowCount()).isEqualTo(2);
        assertThat(ready.downloadUrl()).contains("https://example.test/");

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(storage.single()))) {
            Sheet sheet = workbook.getSheetAt(0);
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("编码");
            assertThat(sheet.getRow(0).getCell(14).getStringCellValue()).isEqualTo("更新时间");
            assertThat(sheet.getLastRowNum()).isEqualTo(2);
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo("EXPORT_A");
            assertThat(sheet.getRow(2).getCell(0).getStringCellValue()).isEqualTo("EXPORT_B");
        }
    }

    @Test
    void exportJobCanTargetSelectedIds() throws IOException {
        var a = standardService.create(scope.tenant(), scope.environmentId(), scope.applicationId(),
                new CreateCommand("PICK_A", null, content("选中 A")));
        standardService.create(scope.tenant(), scope.environmentId(), scope.applicationId(),
                new CreateCommand("SKIP_B", null, content("跳过 B")));
        var c = standardService.create(scope.tenant(), scope.environmentId(), scope.applicationId(),
                new CreateCommand("PICK_C", null, content("选中 C")));

        var queued = exportService.create(scope.tenant(), new CreateExportCommand(
                scope.environmentId(), scope.applicationId(), null, null, null, List.of(a.id(), c.id())));
        exportService.processNextJob();

        var ready = exportService.get(scope.tenant(), queued.id());
        assertThat(ready.status()).isEqualTo("READY");
        assertThat(ready.rowCount()).isEqualTo(2);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(storage.single()))) {
            Sheet sheet = workbook.getSheetAt(0);
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo("PICK_A");
            assertThat(sheet.getRow(2).getCell(0).getStringCellValue()).isEqualTo("PICK_C");
            assertThat(sheet.getLastRowNum()).isEqualTo(2);
        }
    }

    private static byte[] workbook(String[][] rows) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("数标");
            String[] headers = {"编码*", "分类", "名称*", "数据类型*", "长度", "精度",
                    "必填(是/否)", "默认值", "枚举值", "正则", "示例值", "描述"};
            Row header = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }
            for (int r = 0; r < rows.length; r++) {
                Row row = sheet.createRow(r + 1);
                for (int c = 0; c < rows[r].length; c++) {
                    row.createCell(c).setCellValue(rows[r][c]);
                }
            }
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private static StandardContent content(String name) {
        return new StandardContent(name, null, "VARCHAR", 32, null, false, null, null, null, null);
    }
}
