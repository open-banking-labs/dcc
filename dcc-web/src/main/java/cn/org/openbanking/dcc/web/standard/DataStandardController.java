package cn.org.openbanking.dcc.web.standard;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import cn.org.openbanking.dcc.application.standard.DataStandardService;
import cn.org.openbanking.dcc.application.standard.DataStandardService.CreateCommand;
import cn.org.openbanking.dcc.application.standard.DataStandardService.DiffView;
import cn.org.openbanking.dcc.application.standard.DataStandardService.ImportResult;
import cn.org.openbanking.dcc.application.standard.DataStandardService.StandardSummaryView;
import cn.org.openbanking.dcc.application.standard.DataStandardService.StandardView;
import cn.org.openbanking.dcc.application.standard.DataStandardService.UpdateCommand;
import cn.org.openbanking.dcc.application.standard.DataStandardService.VersionView;
import cn.org.openbanking.dcc.application.standard.StandardExportItem;
import cn.org.openbanking.dcc.application.standard.excel.StandardExcelService;
import cn.org.openbanking.dcc.application.standard.export.StandardExportService;
import cn.org.openbanking.dcc.application.standard.export.StandardExportService.CreateExportCommand;
import cn.org.openbanking.dcc.application.standard.export.StandardExportService.ExportJobView;
import cn.org.openbanking.dcc.core.common.error.ValidationException;
import cn.org.openbanking.dcc.core.standard.StandardStatus;
import cn.org.openbanking.dcc.web.common.AbstractTenantController;

import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * REST endpoints for data standards (数标): CRUD, filtered search, versioning
 * (history/diff/rollback), lifecycle transitions and import/export. All operations
 * run inside the caller's tenant.
 */
@RestController
@RequestMapping("/api/standards")
public class DataStandardController extends AbstractTenantController {

    private static final String EXCEL_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final DataStandardService service;
    private final StandardExportService exportService;
    private final StandardExcelService excelService;

    public DataStandardController(DataStandardService service,
            StandardExportService exportService,
            StandardExcelService excelService) {
        this.service = service;
        this.exportService = exportService;
        this.excelService = excelService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority(@perms.artifactWrite)")
    public StandardView create(@RequestParam Long environmentId,
            @RequestParam Long applicationId,
            @Valid @RequestBody CreateCommand command) {
        return service.create(currentTenantId(), environmentId, applicationId, command);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority(@perms.artifactRead)")
    public StandardView get(@PathVariable Long id) {
        return service.get(currentTenantId(), id);
    }

    /**
     * Paged console search. {@code keyword} matches the code or the current version's
     * name, case-insensitively; {@code size} is capped by the service.
     */
    @GetMapping("/page")
    @PreAuthorize("hasAuthority(@perms.artifactRead)")
    public Page<StandardSummaryView> page(@RequestParam(required = false) Long environmentId,
            @RequestParam(required = false) Long applicationId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) StandardStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.query(currentTenantId(), environmentId, applicationId, keyword, category, status,
                page, size);
    }

    /** Lightweight summaries for an explicit id selection (reference analysis). */
    @GetMapping("/summaries")
    @PreAuthorize("hasAuthority(@perms.artifactRead)")
    public List<StandardSummaryView> summaries(@RequestParam List<Long> ids) {
        return service.summaries(currentTenantId(), ids);
    }

    /** Enqueues an asynchronous Excel export; poll the returned job for its link. */
    @PostMapping("/exports")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasAuthority(@perms.artifactRead)")
    public ExportJobView createExport(@Valid @RequestBody CreateExportCommand command) {
        return exportService.create(currentTenantId(), command);
    }

    @GetMapping("/exports/{id}")
    @PreAuthorize("hasAuthority(@perms.artifactRead)")
    public ExportJobView exportJob(@PathVariable Long id) {
        return exportService.get(currentTenantId(), id);
    }

    /** Blank .xlsx template with the accepted headers and a usage sheet. */
    @GetMapping("/import-template")
    @PreAuthorize("hasAuthority(@perms.artifactWrite)")
    public ResponseEntity<byte[]> importTemplate() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"standards-import-template.xlsx\"")
                .contentType(MediaType.parseMediaType(EXCEL_CONTENT_TYPE))
                .body(excelService.importTemplate());
    }

    /**
     * Validates the whole workbook before writing anything; row errors come back in
     * {@link ImportResult#errors()} and no row is applied when any is present.
     */
    @PostMapping(value = "/import-excel", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority(@perms.artifactWrite)")
    public ImportResult importExcel(@RequestParam("file") MultipartFile file,
            @RequestParam Long environmentId,
            @RequestParam Long applicationId) {
        try (InputStream input = file.getInputStream()) {
            return excelService.importXlsx(currentTenantId(), environmentId, applicationId,
                    file.getOriginalFilename(), input);
        } catch (IOException ex) {
            throw new ValidationException("无法读取上传文件");
        }
    }

    @GetMapping
    @PreAuthorize("hasAuthority(@perms.artifactRead)")
    public List<StandardView> search(@RequestParam(required = false) Long environmentId,
            @RequestParam(required = false) Long applicationId,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) StandardStatus status) {
        return service.search(currentTenantId(), environmentId, applicationId, category, status);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@scopeGuard.mayWriteArtifact('DATA_STANDARD', #id)")
    public StandardView update(@PathVariable Long id, @Valid @RequestBody UpdateCommand command) {
        return service.update(currentTenantId(), id, command);
    }

    @PostMapping("/{id}/rollback")
    @PreAuthorize("@scopeGuard.mayWriteArtifact('DATA_STANDARD', #id)")
    public StandardView rollback(@PathVariable Long id, @RequestParam String version) {
        return service.rollback(currentTenantId(), id, version);
    }

    @PostMapping("/{id}/status")
    @PreAuthorize("@scopeGuard.mayTransition('DATA_STANDARD', #id, #target)")
    public StandardView transition(@PathVariable Long id, @RequestParam StandardStatus target) {
        return service.transition(currentTenantId(), id, target);
    }

    @GetMapping("/{id}/history")
    @PreAuthorize("hasAuthority(@perms.artifactRead)")
    public List<VersionView> history(@PathVariable Long id) {
        return service.history(currentTenantId(), id);
    }

    @GetMapping("/{id}/versions/{version}/verify")
    @PreAuthorize("hasAuthority(@perms.artifactRead)")
    public boolean verify(@PathVariable Long id, @PathVariable String version) {
        return service.verify(currentTenantId(), id, version);
    }

    @GetMapping("/{id}/references")
    @PreAuthorize("hasAuthority(@perms.artifactRead)")
    public List<String> references(@PathVariable Long id) {
        return service.references(currentTenantId(), id);
    }

    @GetMapping("/{id}/diff")
    @PreAuthorize("hasAuthority(@perms.artifactRead)")
    public DiffView diff(@PathVariable Long id, @RequestParam String from, @RequestParam String to) {
        return service.diff(currentTenantId(), id, from, to);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@scopeGuard.mayDeleteArtifact('DATA_STANDARD', #id)")
    public void delete(@PathVariable Long id) {
        service.delete(currentTenantId(), id);
    }

    /** Brings a logically-deleted standard back into the default view. */
    @PostMapping("/{id}/restore")
    @PreAuthorize("hasAuthority(@perms.artifactRestore)")
    public StandardView restore(@PathVariable Long id) {
        return service.restore(currentTenantId(), id);
    }

    @GetMapping("/export")
    @PreAuthorize("hasAuthority(@perms.artifactRead)")
    public List<StandardExportItem> export(@RequestParam Long environmentId, @RequestParam Long applicationId) {
        return service.export(currentTenantId(), environmentId, applicationId);
    }

    @PostMapping("/import")
    @PreAuthorize("hasAuthority(@perms.artifactWrite)")
    public ImportResult importItems(@RequestParam Long environmentId,
            @RequestParam Long applicationId,
            @RequestBody List<StandardExportItem> items) {
        return service.importItems(currentTenantId(), environmentId, applicationId, items);
    }
}
