package cn.org.openbanking.dcc.web.tenant;

import cn.org.openbanking.dcc.application.tenant.RoleAssignmentService;
import cn.org.openbanking.dcc.application.tenant.RoleAssignmentService.AssignmentView;
import cn.org.openbanking.dcc.application.tenant.TenantMembershipService;
import cn.org.openbanking.dcc.application.tenant.TenantMembershipService.MembershipView;
import cn.org.openbanking.dcc.core.tenant.ScopeType;
import cn.org.openbanking.dcc.security.DccSecurityProperties;
import cn.org.openbanking.dcc.web.common.AbstractTenantController;
import cn.org.openbanking.dcc.web.common.GrantPolicy;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tenant-scoped directory administration: the tenant administrator grants memberships and roles
 * <em>inside its own tenant</em> (ADR 0005 §D1/§D5).
 *
 * <p>Two rules make this endpoint safe where {@code /api/directory} (platform, bootstrap-only) is
 * not:
 * <ul>
 *   <li>the target tenant is always the caller's tenant — it is never accepted from the request
 *       body, so a tenant administrator cannot reach into another tenant;</li>
 *   <li>only {@code dcc.security.tenant-grantable-roles} may be granted, so a platform role cannot
 *       be laundered through a directory row.</li>
 * </ul>
 */
@RestController
public class TenantDirectoryController extends AbstractTenantController {

    /** Membership grant; the tenant comes from the caller, never from the body. */
    public record GrantMembershipRequest(@NotBlank @Size(max = 128) String subject) {
    }

    /** Role grant; the tenant comes from the caller, never from the body. */
    public record GrantRoleRequest(@NotBlank @Size(max = 128) String subject,
            @NotBlank @Size(max = 64) String role,
            ScopeType scopeType,
            @Size(max = 64) String scopeCode) {
    }

    private final TenantMembershipService memberships;
    private final RoleAssignmentService assignments;
    private final GrantPolicy grantPolicy;
    private final DccSecurityProperties properties;

    public TenantDirectoryController(TenantMembershipService memberships, RoleAssignmentService assignments,
            GrantPolicy grantPolicy, DccSecurityProperties properties) {
        this.memberships = memberships;
        this.assignments = assignments;
        this.grantPolicy = grantPolicy;
        this.properties = properties;
    }

    // ------------------------------------------------------------ memberships

    /** Grants a subject access to the caller's tenant. */
    @PostMapping("/api/memberships")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority(@perms.directoryWrite)")
    public MembershipView createMembership(@Valid @RequestBody GrantMembershipRequest request) {
        return memberships.create(
                new TenantMembershipService.CreateCommand(request.subject(), currentTenantId()));
    }

    @GetMapping("/api/memberships")
    @PreAuthorize("hasAuthority(@perms.directoryRead)")
    public Page<MembershipView> searchMemberships(@RequestParam(required = false) String subject,
            @PageableDefault(size = 50) Pageable pageable) {
        return memberships.search(subject, currentTenantId(), pageable);
    }

    @DeleteMapping("/api/memberships/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority(@perms.directoryWrite)")
    public void deleteMembership(@PathVariable Long id) {
        memberships.deleteInTenant(currentTenantId(), id);
    }

    @PostMapping("/api/memberships/{id}/restore")
    @PreAuthorize("hasAuthority(@perms.directoryWrite)")
    public MembershipView restoreMembership(@PathVariable Long id) {
        return memberships.restoreInTenant(currentTenantId(), id);
    }

    // ---------------------------------------------------------- assignments

    /** Grants one grantable role to a subject inside the caller's tenant. */
    @PostMapping("/api/role-assignments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority(@perms.directoryWrite)")
    public AssignmentView createAssignment(@Valid @RequestBody GrantRoleRequest request) {
        grantPolicy.requireTenantGrant(request.role(), request.scopeType());
        return assignments.create(new RoleAssignmentService.CreateCommand(request.subject(), currentTenantId(),
                request.role(), request.scopeType(), request.scopeCode()));
    }

    @GetMapping("/api/role-assignments")
    @PreAuthorize("hasAuthority(@perms.directoryRead)")
    public Page<AssignmentView> searchAssignments(@RequestParam(required = false) String subject,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) ScopeType scopeType,
            @RequestParam(required = false) String scopeCode,
            @PageableDefault(size = 50) Pageable pageable) {
        return assignments.search(subject, currentTenantId(), role, scopeType, scopeCode, pageable);
    }

    @DeleteMapping("/api/role-assignments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority(@perms.directoryWrite)")
    public void deleteAssignment(@PathVariable Long id) {
        assignments.deleteInTenant(currentTenantId(), id);
    }

    @PostMapping("/api/role-assignments/{id}/restore")
    @PreAuthorize("hasAuthority(@perms.directoryWrite)")
    public AssignmentView restoreAssignment(@PathVariable Long id) {
        return assignments.restoreInTenant(currentTenantId(), id);
    }

    /** Confirms a pending privileged grant of this tenant (dual control, ADR 0005 §D10). */
    @PostMapping("/api/role-assignments/{id}/confirm")
    @PreAuthorize("hasAuthority(@perms.directoryWrite)")
    public AssignmentView confirmAssignment(@PathVariable Long id) {
        return assignments.confirmInTenant(currentTenantId(), id);
    }

}
