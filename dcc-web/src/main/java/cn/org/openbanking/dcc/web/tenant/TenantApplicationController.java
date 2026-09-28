package cn.org.openbanking.dcc.web.tenant;

import java.util.List;

import cn.org.openbanking.dcc.application.tenant.TenantApplicationService;
import cn.org.openbanking.dcc.application.tenant.TenantApplicationService.ApplicationView;
import cn.org.openbanking.dcc.application.tenant.TenantApplicationService.CreateCommand;
import cn.org.openbanking.dcc.application.tenant.TenantApplicationService.UpdateCommand;
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

/** REST endpoints for a tenant's applications (公共基础 / 账务系统 / 客户系统). */
@RestController
@RequestMapping("/api/applications")
public class TenantApplicationController extends AbstractTenantController {

    private final TenantApplicationService service;

    public TenantApplicationController(TenantApplicationService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApplicationView create(@Valid @RequestBody CreateCommand command) {
        return service.create(currentTenantId(), command);
    }

    @GetMapping
    public List<ApplicationView> list() {
        return service.list(currentTenantId());
    }

    @GetMapping("/{id}")
    public ApplicationView get(@PathVariable Long id) {
        return service.get(currentTenantId(), id);
    }

    @PutMapping("/{id}")
    public ApplicationView update(@PathVariable Long id, @Valid @RequestBody UpdateCommand command) {
        return service.update(currentTenantId(), id, command);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(currentTenantId(), id);
    }
}
