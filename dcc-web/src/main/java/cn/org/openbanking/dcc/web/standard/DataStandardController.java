package cn.org.openbanking.dcc.web.standard;

import java.util.List;

import cn.org.openbanking.dcc.application.standard.DataStandardService;
import cn.org.openbanking.dcc.application.standard.DataStandardService.CreateCommand;
import cn.org.openbanking.dcc.application.standard.DataStandardService.DiffView;
import cn.org.openbanking.dcc.application.standard.DataStandardService.ImportResult;
import cn.org.openbanking.dcc.application.standard.DataStandardService.StandardView;
import cn.org.openbanking.dcc.application.standard.DataStandardService.UpdateCommand;
import cn.org.openbanking.dcc.application.standard.DataStandardService.VersionView;
import cn.org.openbanking.dcc.application.standard.StandardExportItem;
import cn.org.openbanking.dcc.core.standard.StandardStatus;
import cn.org.openbanking.dcc.web.common.AbstractTenantController;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.HttpStatus;
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

/**
 * REST endpoints for data standards (数标): CRUD, filtered search, versioning
 * (history/diff/rollback), lifecycle transitions and import/export. All operations
 * run inside the caller's tenant.
 */
@RestController
@RequestMapping("/api/standards")
public class DataStandardController extends AbstractTenantController {

    private final DataStandardService service;

    public DataStandardController(DataStandardService service) {
        this.service = service;
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
