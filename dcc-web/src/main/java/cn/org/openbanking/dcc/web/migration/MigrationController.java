package cn.org.openbanking.dcc.web.migration;

import java.util.List;

import cn.org.openbanking.dcc.application.migration.MigrationService;
import cn.org.openbanking.dcc.application.migration.MigrationService.CreateCommand;
import cn.org.openbanking.dcc.application.migration.MigrationService.MigrationOrderView;
import cn.org.openbanking.dcc.core.migration.MigrationStatus;
import cn.org.openbanking.dcc.web.common.AbstractTenantController;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for environment migration and its approval flow. All operations run
 * inside the caller's tenant.
 */
@RestController
@RequestMapping("/api/migrations")
public class MigrationController extends AbstractTenantController {

    private final MigrationService service;

    public MigrationController(MigrationService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MigrationOrderView create(@Valid @RequestBody CreateCommand command) {
        return service.create(currentTenantId(), command);
    }

    @GetMapping("/{id}")
    public MigrationOrderView get(@PathVariable Long id) {
        return service.get(currentTenantId(), id);
    }

    @GetMapping
    public List<MigrationOrderView> search(@RequestParam(required = false) Long targetEnvironmentId,
            @RequestParam(required = false) MigrationStatus status) {
        return service.search(currentTenantId(), targetEnvironmentId, status);
    }

    @PostMapping("/{id}/submit")
    public MigrationOrderView submit(@PathVariable Long id) {
        return service.submit(currentTenantId(), id);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority(@dccAuthorities.approver)")
    public MigrationOrderView approve(@PathVariable Long id) {
        return service.approve(currentTenantId(), id);
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority(@dccAuthorities.approver)")
    public MigrationOrderView reject(@PathVariable Long id) {
        return service.reject(currentTenantId(), id);
    }

    @PostMapping("/{id}/execute")
    public MigrationOrderView execute(@PathVariable Long id) {
        return service.execute(currentTenantId(), id);
    }

    @PostMapping("/{id}/rollback")
    public MigrationOrderView rollback(@PathVariable Long id) {
        return service.rollback(currentTenantId(), id);
    }
}
