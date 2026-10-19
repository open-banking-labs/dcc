package cn.org.openbanking.dcc.web.casebook;

import java.util.List;

import cn.org.openbanking.dcc.application.casebook.ReferenceCaseService;
import cn.org.openbanking.dcc.application.casebook.ReferenceCaseService.CaseAdoptionView;
import cn.org.openbanking.dcc.application.casebook.ReferenceCaseService.CaseReachView;
import cn.org.openbanking.dcc.web.common.AbstractTenantController;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The reference-case adoption index (ADR 0005 §D7).
 *
 * <p>Two views over the same rows, deliberately split by layer:
 * <ul>
 *   <li>{@code GET /api/case-adoptions} — the tenant's own copies, with a {@code stale} flag once the
 *       case has moved to a newer version. Copies are never rewritten automatically.</li>
 *   <li>{@code GET /api/platform/case-adoptions/{caseId}} — the case author's reach: which tenants
 *       adopted which version. No tenant artifact identifier is exposed, so the platform still cannot
 *       read tenant content.</li>
 * </ul>
 */
@RestController
public class CaseAdoptionController extends AbstractTenantController {

    private final ReferenceCaseService service;

    public CaseAdoptionController(ReferenceCaseService service) {
        this.service = service;
    }

    /** The caller tenant's adoptions, newest first. */
    @GetMapping("/api/case-adoptions")
    @PreAuthorize("hasAuthority(@perms.caseRead)")
    public List<CaseAdoptionView> tenantAdoptions() {
        return service.adoptionsOf(currentTenantId());
    }

    /** How far one case travelled: every tenant that adopted it, newest first (platform only). */
    @GetMapping("/api/platform/case-adoptions/{caseId}")
    @PreAuthorize("hasAuthority(@perms.caseWrite)")
    public List<CaseReachView> caseReach(@PathVariable Long caseId) {
        return service.reachOf(caseId);
    }
}
