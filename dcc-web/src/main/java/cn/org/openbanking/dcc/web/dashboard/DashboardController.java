package cn.org.openbanking.dcc.web.dashboard;

import java.util.Set;
import java.util.stream.Collectors;

import cn.org.openbanking.dcc.application.dashboard.DashboardService;
import cn.org.openbanking.dcc.application.dashboard.DashboardService.Query;
import cn.org.openbanking.dcc.application.dashboard.DashboardService.SummaryView;
import cn.org.openbanking.dcc.security.DccPermissions;
import cn.org.openbanking.dcc.web.common.AbstractTenantController;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The workbench dashboard's single read.
 *
 * <p>Deliberately {@code isAuthenticated()} rather than one permission point: a dashboard is a
 * <em>composite</em> of every plane, so no single point fits — a platform administrator without
 * {@code artifact.read} must still land somewhere after signing in. Authorization therefore happens
 * per section: this controller resolves which sections the caller's authorities cover and the use
 * case omits the rest ({@code null}), so nothing outside the caller's rights is ever counted.
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController extends AbstractTenantController {

    private final DashboardService service;
    private final DccPermissions permissions;

    public DashboardController(DashboardService service, DccPermissions permissions) {
        this.service = service;
        this.permissions = permissions;
    }

    /**
     * @param environmentId optional scope filter (the console's environment switcher)
     * @param applicationId optional scope filter (the console's application switcher)
     * @return the sections the caller may read; the rest are {@code null}
     */
    @GetMapping("/summary")
    @PreAuthorize("isAuthenticated()")
    public SummaryView summary(@RequestParam(required = false) Long environmentId,
            @RequestParam(required = false) Long applicationId,
            Authentication authentication) {
        Set<String> granted = granted(authentication);
        return service.summary(currentTenantId(), new Query(environmentId, applicationId,
                granted.contains(permissions.getArtifactRead()),
                // anyone who authors, approves or executes migrations operates that queue: the tenant
                // administrator carries only migration.execute, maintainers only migration.write
                granted.contains(permissions.getMigrationWrite())
                        || granted.contains(permissions.getMigrationApprove())
                        || granted.contains(permissions.getMigrationExecute()),
                granted.contains(permissions.getAuditBusiness()),
                granted.contains(permissions.getAuditPlatform()),
                granted.contains(permissions.getTenantAdmit())));
    }

    private static Set<String> granted(Authentication authentication) {
        if (authentication == null || authentication.getAuthorities() == null) {
            return Set.of();
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toUnmodifiableSet());
    }
}
