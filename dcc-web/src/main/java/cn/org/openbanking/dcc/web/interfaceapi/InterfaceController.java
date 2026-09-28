package cn.org.openbanking.dcc.web.interfaceapi;

import java.util.List;

import cn.org.openbanking.dcc.application.interfaceapi.InterfaceService;
import cn.org.openbanking.dcc.application.interfaceapi.InterfaceService.CreateCommand;
import cn.org.openbanking.dcc.application.interfaceapi.InterfaceService.InterfaceDiffView;
import cn.org.openbanking.dcc.application.interfaceapi.InterfaceService.InterfaceView;
import cn.org.openbanking.dcc.application.interfaceapi.InterfaceService.UpdateCommand;
import cn.org.openbanking.dcc.application.interfaceapi.InterfaceService.VersionView;
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

/** REST endpoints for business interfaces. All operations run inside the caller's tenant. */
@RestController
@RequestMapping("/api/interfaces")
public class InterfaceController extends AbstractTenantController {

    private final InterfaceService service;

    public InterfaceController(InterfaceService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InterfaceView create(@RequestParam Long environmentId,
            @RequestParam Long applicationId,
            @Valid @RequestBody CreateCommand command) {
        return service.create(currentTenantId(), environmentId, applicationId, command);
    }

    @GetMapping("/{id}")
    public InterfaceView get(@PathVariable Long id) {
        return service.get(currentTenantId(), id);
    }

    @GetMapping
    public List<InterfaceView> search(@RequestParam(required = false) Long environmentId,
            @RequestParam(required = false) Long applicationId,
            @RequestParam(required = false) StandardStatus status) {
        return service.search(currentTenantId(), environmentId, applicationId, status);
    }

    @PutMapping("/{id}")
    public InterfaceView update(@PathVariable Long id, @Valid @RequestBody UpdateCommand command) {
        return service.update(currentTenantId(), id, command);
    }

    @PostMapping("/{id}/rollback")
    public InterfaceView rollback(@PathVariable Long id, @RequestParam String version) {
        return service.rollback(currentTenantId(), id, version);
    }

    @PostMapping("/{id}/status")
    @PreAuthorize("hasAuthority('DCC_APPROVER')")
    public InterfaceView transition(@PathVariable Long id, @RequestParam StandardStatus target) {
        return service.transition(currentTenantId(), id, target);
    }

    @GetMapping("/{id}/history")
    public List<VersionView> history(@PathVariable Long id) {
        return service.history(currentTenantId(), id);
    }

    @GetMapping("/{id}/versions/{version}/verify")
    public boolean verify(@PathVariable Long id, @PathVariable String version) {
        return service.verify(currentTenantId(), id, version);
    }

    @GetMapping("/{id}/diff")
    public InterfaceDiffView diff(@PathVariable Long id, @RequestParam String from, @RequestParam String to) {
        return service.diff(currentTenantId(), id, from, to);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('DCC_ADMIN')")
    public void delete(@PathVariable Long id) {
        service.delete(currentTenantId(), id);
    }
}
