package cn.org.openbanking.dcc.web.audit;

import java.time.Instant;
import java.util.List;

import cn.org.openbanking.dcc.application.audit.AuditService;
import cn.org.openbanking.dcc.application.audit.AuditService.AuditView;
import cn.org.openbanking.dcc.application.audit.AuditService.VerifyView;
import cn.org.openbanking.dcc.core.common.audit.AuditAction;
import cn.org.openbanking.dcc.web.common.AbstractTenantController;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only access to the append-only audit trail for the caller's tenant. Restricted to
 * administrators because it exposes who changed what and the before/after images.
 */
@RestController
@RequestMapping("/api/audit")
@PreAuthorize("hasAuthority(@dccAuthorities.admin)")
public class AuditController extends AbstractTenantController {

    private final AuditService service;

    public AuditController(AuditService service) {
        this.service = service;
    }

    /** Filtered, paged search, newest first; all filters are optional. */
    @GetMapping
    public Page<AuditView> search(
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) Long entityId,
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @PageableDefault(size = 50) Pageable pageable) {
        return service.search(currentTenantId(), entityType, entityId, actor, action, from, to, pageable);
    }

    /** The full, oldest-first history of one row. */
    @GetMapping("/{entityType}/{entityId}")
    public List<AuditView> history(@PathVariable String entityType, @PathVariable Long entityId) {
        return service.history(currentTenantId(), entityType, entityId);
    }

    /** Tamper check: recomputes and compares the hash chain of one row's history. */
    @GetMapping("/{entityType}/{entityId}/verify")
    public VerifyView verify(@PathVariable String entityType, @PathVariable Long entityId) {
        return service.verify(currentTenantId(), entityType, entityId);
    }
}
