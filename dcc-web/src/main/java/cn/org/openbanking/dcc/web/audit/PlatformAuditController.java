package cn.org.openbanking.dcc.web.audit;

import java.time.Instant;

import cn.org.openbanking.dcc.application.audit.AuditService;
import cn.org.openbanking.dcc.application.audit.AuditService.AuditView;
import cn.org.openbanking.dcc.core.common.audit.AuditAction;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only access to <em>platform-level</em> audit rows: tenant admission, directory grants and
 * reference-case changes (ADR 0005 §D6).
 *
 * <p>These rows carry no {@code tenant_id}, so the tenant-scoped {@link AuditController} can never
 * return them — without this endpoint the platform administrator would have no audit trail for
 * their own actions, which are exactly the actions that hand out power. Mounted under
 * {@code /api/platform/audit} rather than {@code /api/audit/platform} to stay clear of the
 * {@code /api/audit/{entityType}/{entityId}} route pattern.
 */
@RestController
@RequestMapping("/api/platform/audit")
@PreAuthorize("hasAuthority(@perms.auditPlatform)")
public class PlatformAuditController {

    private final AuditService service;

    public PlatformAuditController(AuditService service) {
        this.service = service;
    }

    /** Filtered, paged search over platform-level entries, newest first; all filters optional. */
    @GetMapping
    public Page<AuditView> search(
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) Long entityId,
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @PageableDefault(size = 50) Pageable pageable) {
        return service.searchPlatform(entityType, entityId, actor, action, from, to, pageable);
    }
}
