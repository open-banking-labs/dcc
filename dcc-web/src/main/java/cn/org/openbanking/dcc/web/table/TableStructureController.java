package cn.org.openbanking.dcc.web.table;

import java.util.List;

import cn.org.openbanking.dcc.application.table.TableStructureService;
import cn.org.openbanking.dcc.application.table.TableStructureService.CreateCommand;
import cn.org.openbanking.dcc.application.table.TableStructureService.TableDiffView;
import cn.org.openbanking.dcc.application.table.TableStructureService.TableView;
import cn.org.openbanking.dcc.application.table.TableStructureService.UpdateCommand;
import cn.org.openbanking.dcc.application.table.TableStructureService.VersionView;
import cn.org.openbanking.dcc.core.standard.StandardStatus;
import cn.org.openbanking.dcc.generator.ddl.TableDdl;
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
 * REST endpoints for table structures: CRUD, versioning, DDL / Flyway SQL
 * generation. All operations run inside the caller's tenant.
 */
@RestController
@RequestMapping("/api/tables")
public class TableStructureController extends AbstractTenantController {

    private final TableStructureService service;

    public TableStructureController(TableStructureService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority(@perms.artifactWrite)")
    public TableView create(@RequestParam Long environmentId,
            @RequestParam Long applicationId,
            @Valid @RequestBody CreateCommand command) {
        return service.create(currentTenantId(), environmentId, applicationId, command);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority(@perms.artifactRead)")
    public TableView get(@PathVariable Long id) {
        return service.get(currentTenantId(), id);
    }

    @GetMapping
    @PreAuthorize("hasAuthority(@perms.artifactRead)")
    public List<TableView> search(@RequestParam(required = false) Long environmentId,
            @RequestParam(required = false) Long applicationId,
            @RequestParam(required = false) StandardStatus status) {
        return service.search(currentTenantId(), environmentId, applicationId, status);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@scopeGuard.mayWriteArtifact('TABLE_STRUCTURE', #id)")
    public TableView update(@PathVariable Long id, @Valid @RequestBody UpdateCommand command) {
        return service.update(currentTenantId(), id, command);
    }

    @PostMapping("/{id}/rollback")
    @PreAuthorize("@scopeGuard.mayWriteArtifact('TABLE_STRUCTURE', #id)")
    public TableView rollback(@PathVariable Long id, @RequestParam String version) {
        return service.rollback(currentTenantId(), id, version);
    }

    @PostMapping("/{id}/status")
    @PreAuthorize("@scopeGuard.mayTransition('TABLE_STRUCTURE', #id, #target)")
    public TableView transition(@PathVariable Long id, @RequestParam StandardStatus target) {
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

    @GetMapping("/{id}/diff")
    @PreAuthorize("hasAuthority(@perms.artifactRead)")
    public TableDiffView diff(@PathVariable Long id, @RequestParam String from, @RequestParam String to) {
        return service.diff(currentTenantId(), id, from, to);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@scopeGuard.mayDeleteArtifact('TABLE_STRUCTURE', #id)")
    public void delete(@PathVariable Long id) {
        service.delete(currentTenantId(), id);
    }

    /** Brings a logically-deleted table structure back into the default view. */
    @PostMapping("/{id}/restore")
    @PreAuthorize("hasAuthority(@perms.artifactRestore)")
    public TableView restore(@PathVariable Long id) {
        return service.restore(currentTenantId(), id);
    }

    @GetMapping("/{id}/ddl")
    @PreAuthorize("hasAuthority(@perms.generationPackage)")
    public TableDdl ddl(@PathVariable Long id, @RequestParam(required = false) String schema) {
        return service.createDdl(currentTenantId(), id, schema);
    }

    @GetMapping("/{id}/ddl/alter")
    @PreAuthorize("hasAuthority(@perms.generationPackage)")
    public TableDdl alterDdl(@PathVariable Long id,
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam(required = false) String schema) {
        return service.alterDdl(currentTenantId(), id, from, to, schema);
    }
}
