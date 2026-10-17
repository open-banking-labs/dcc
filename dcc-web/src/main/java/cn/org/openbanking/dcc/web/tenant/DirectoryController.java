package cn.org.openbanking.dcc.web.tenant;

import cn.org.openbanking.dcc.application.tenant.RoleAssignmentService;
import cn.org.openbanking.dcc.application.tenant.RoleAssignmentService.AssignmentView;
import cn.org.openbanking.dcc.application.tenant.TenantMembershipService;
import cn.org.openbanking.dcc.application.tenant.TenantMembershipService.MembershipView;

import jakarta.validation.Valid;

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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authorization directory administration (memberships and tenant-scoped role
 * assignments), consumed by {@code dcc.security.directory-mode} deployments.
 * The directory rows are platform-level, so this controller is not tenant-scoped
 * either: administrators manage every tenant through explicit query parameters.
 */
@RestController
@RequestMapping("/api/directory")
@PreAuthorize("hasAuthority(@dccAuthorities.admin)")
public class DirectoryController {

    private final TenantMembershipService memberships;
    private final RoleAssignmentService assignments;

    public DirectoryController(TenantMembershipService memberships, RoleAssignmentService assignments) {
        this.memberships = memberships;
        this.assignments = assignments;
    }

    // ------------------------------------------------------------ memberships

    /** Filtered, paged membership search, newest first; all filters optional. */
    @GetMapping("/memberships")
    public Page<MembershipView> searchMemberships(
            @RequestParam(required = false) String subject,
            @RequestParam(required = false) String tenant,
            @PageableDefault(size = 50) Pageable pageable) {
        return memberships.search(subject, tenant, pageable);
    }

    /** Grants a subject the permission to act inside the tenant. */
    @PostMapping("/memberships")
    @ResponseStatus(HttpStatus.CREATED)
    public TenantMembershipService.MembershipView createMembership(@Valid @RequestBody TenantMembershipService.CreateCommand command) {
        return memberships.create(command);
    }

    /** Logically deletes a membership (compliance retention). */
    @DeleteMapping("/memberships/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMembership(@PathVariable Long id) {
        memberships.delete(id);
    }

    /** Restores a logically-deleted membership. */
    @PostMapping("/memberships/{id}/restore")
    public TenantMembershipService.MembershipView restoreMembership(@PathVariable Long id) {
        return memberships.restore(id);
    }

    // ---------------------------------------------------------- assignments

    /** Filtered, paged assignment search, newest first; all filters optional. */
    @GetMapping("/role-assignments")
    public Page<AssignmentView> searchAssignments(
            @RequestParam(required = false) String subject,
            @RequestParam(required = false) String tenant,
            @RequestParam(required = false) String role,
            @PageableDefault(size = 50) Pageable pageable) {
        return assignments.search(subject, tenant, role, pageable);
    }

    /** Grants one role to a subject inside the tenant (unioned with claim roles). */
    @PostMapping("/role-assignments")
    @ResponseStatus(HttpStatus.CREATED)
    public AssignmentView createAssignment(@Valid @RequestBody RoleAssignmentService.CreateCommand command) {
        return assignments.create(command);
    }

    /** Logically deletes an assignment (compliance retention). */
    @DeleteMapping("/role-assignments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAssignment(@PathVariable Long id) {
        assignments.delete(id);
    }

    /** Restores a logically-deleted assignment. */
    @PostMapping("/role-assignments/{id}/restore")
    public AssignmentView restoreAssignment(@PathVariable Long id) {
        return assignments.restore(id);
    }
}
