package cn.org.openbanking.dcc.web.tenant;

import java.util.List;

import cn.org.openbanking.dcc.application.tenant.TenantEnvironmentService;
import cn.org.openbanking.dcc.application.tenant.TenantEnvironmentService.CreateCommand;
import cn.org.openbanking.dcc.application.tenant.TenantEnvironmentService.EnvironmentView;
import cn.org.openbanking.dcc.application.tenant.TenantEnvironmentService.UpdateCommand;
import cn.org.openbanking.dcc.web.common.AbstractTenantController;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** REST endpoints for a tenant's environments (DEV / TEST / PROD). */
@RestController
@RequestMapping("/api/environments")
public class TenantEnvironmentController extends AbstractTenantController {

    private final TenantEnvironmentService service;

    public TenantEnvironmentController(TenantEnvironmentService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EnvironmentView create(@Valid @RequestBody CreateCommand command) {
        return service.create(currentTenantId(), command);
    }

    @GetMapping
    public List<EnvironmentView> list() {
        return service.list(currentTenantId());
    }

    @GetMapping("/{id}")
    public EnvironmentView get(@PathVariable Long id) {
        return service.get(currentTenantId(), id);
    }

    @PutMapping("/{id}")
    public EnvironmentView update(@PathVariable Long id, @Valid @RequestBody UpdateCommand command) {
        return service.update(currentTenantId(), id, command);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(currentTenantId(), id);
    }
}
