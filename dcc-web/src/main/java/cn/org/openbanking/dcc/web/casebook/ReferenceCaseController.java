package cn.org.openbanking.dcc.web.casebook;

import java.util.List;

import cn.org.openbanking.dcc.application.casebook.ReferenceCaseService;
import cn.org.openbanking.dcc.application.casebook.ReferenceCaseService.AdoptCommand;
import cn.org.openbanking.dcc.application.casebook.ReferenceCaseService.AdoptResult;
import cn.org.openbanking.dcc.application.casebook.ReferenceCaseService.CaseView;
import cn.org.openbanking.dcc.application.casebook.ReferenceCaseService.CreateCommand;
import cn.org.openbanking.dcc.application.casebook.ReferenceCaseService.UpdateCommand;
import cn.org.openbanking.dcc.core.casebook.ReferenceCaseFeature;
import cn.org.openbanking.dcc.web.common.AbstractTenantController;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
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
 * REST endpoints for the reference-case library (指导性案例, ADR 0005 §D7).
 *
 * <p>Reading the library and adopting a case are tenant-plane operations ({@code case.read} /
 * {@code case.adopt}); authoring the library is platform-plane ({@code case.write}) — a tenant
 * administrator can never publish guidance for other tenants, and the system administrator's
 * authoring right is a permission point, not membership in some hidden tenant.
 */
@RestController
@RequestMapping("/api/cases")
public class ReferenceCaseController extends AbstractTenantController {

    private final ReferenceCaseService service;

    public ReferenceCaseController(ReferenceCaseService service) {
        this.service = service;
    }

    /** Lists the library; {@code feature} narrows to one kind, {@code adoptableOnly} hides disabled cases. */
    @GetMapping
    @PreAuthorize("hasAuthority(@perms.caseRead)")
    public List<CaseView> list(@RequestParam(required = false) ReferenceCaseFeature feature,
            @RequestParam(defaultValue = "false") boolean adoptableOnly) {
        return service.list(feature, adoptableOnly);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority(@perms.caseRead)")
    public CaseView get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority(@perms.caseWrite)")
    public CaseView create(@Valid @RequestBody CreateCommand command) {
        return service.create(command);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority(@perms.caseWrite)")
    public CaseView update(@PathVariable Long id, @Valid @RequestBody UpdateCommand command) {
        return service.update(id, command);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority(@perms.caseWrite)")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }

    @PostMapping("/{id}/restore")
    @PreAuthorize("hasAuthority(@perms.caseWrite)")
    public CaseView restore(@PathVariable Long id) {
        return service.restore(id);
    }

    /** Adopts the case into the caller's tenant as a draft artifact (or the skeleton). */
    @PostMapping("/{id}/adopt")
    @PreAuthorize("hasAuthority(@perms.caseAdopt)")
    public AdoptResult adopt(@PathVariable Long id, @RequestBody(required = false) AdoptCommand command) {
        return service.adopt(currentTenantId(), id, command == null ? new AdoptCommand(null, null, null, null)
                : command);
    }
}
