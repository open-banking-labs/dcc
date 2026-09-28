package cn.org.openbanking.dcc.web.tenant;

import java.util.List;

import cn.org.openbanking.dcc.application.tenant.TenantService;
import cn.org.openbanking.dcc.application.tenant.TenantService.CreateCommand;
import cn.org.openbanking.dcc.application.tenant.TenantService.TenantView;
import cn.org.openbanking.dcc.application.tenant.TenantService.UpdateCommand;

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

/**
 * Platform-level REST endpoints for tenant management. Tenant-scoped operations
 * (environments, applications, standards) live in their own controllers and take
 * the tenant from the caller's token.
 */
@RestController
@RequestMapping("/api/tenants")
public class TenantController {

    private final TenantService service;

    public TenantController(TenantService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TenantView create(@Valid @RequestBody CreateCommand command) {
        return service.create(command);
    }

    @GetMapping
    public List<TenantView> list() {
        return service.list();
    }

    @GetMapping("/{code}")
    public TenantView get(@PathVariable String code) {
        return service.getByCode(code);
    }

    @PutMapping("/{code}")
    public TenantView update(@PathVariable String code, @Valid @RequestBody UpdateCommand command) {
        return service.update(code, command);
    }

    @DeleteMapping("/{code}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String code) {
        service.delete(code);
    }
}
