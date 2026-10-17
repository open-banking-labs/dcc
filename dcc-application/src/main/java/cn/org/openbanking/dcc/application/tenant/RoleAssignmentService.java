package cn.org.openbanking.dcc.application.tenant;

import java.time.Instant;

import cn.org.openbanking.dcc.application.common.ChangeContext;
import cn.org.openbanking.dcc.core.common.error.ConflictException;
import cn.org.openbanking.dcc.core.common.error.ResourceNotFoundException;
import cn.org.openbanking.dcc.core.common.error.ValidationException;
import cn.org.openbanking.dcc.core.tenant.RoleAssignment;
import cn.org.openbanking.dcc.core.tenant.repository.RoleAssignmentRepository;
import cn.org.openbanking.dcc.core.tenant.repository.TenantRepository;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use cases for managing the authorization directory (role assignments). An
 * assignment grants one role to a subject inside one tenant; in directory mode it
 * is unioned with the token's claim roles before authority mapping, so deployments
 * can assign IdP role names ({@code dcc.security.role-authorities} maps them) or
 * logical authority names directly. Audited/retained like every other row and
 * managed platform-level (not tenant-scoped).
 */
@Service
@Transactional
public class RoleAssignmentService {

    public record CreateCommand(
            @NotBlank @Size(max = 128) String subject,
            @NotBlank @Size(max = 64) String tenantCode,
            @NotBlank @Size(max = 64) String role) {
    }

    public record AssignmentView(Long id, String subject, String tenantCode, String role,
            Instant createdAt, Instant updatedAt) {
    }

    private final RoleAssignmentRepository assignments;
    private final TenantRepository tenants;
    private final ChangeContext changeContext;

    public RoleAssignmentService(RoleAssignmentRepository assignments, TenantRepository tenants,
            ChangeContext changeContext) {
        this.assignments = assignments;
        this.tenants = tenants;
        this.changeContext = changeContext;
    }

    @Transactional
    public AssignmentView create(CreateCommand command) {
        requireActiveTenant(command.tenantCode());
        try {
            return toView(assignments.saveAndFlush(
                    new RoleAssignment(command.subject(), command.tenantCode(), command.role())));
        } catch (DataIntegrityViolationException ex) {
            // partial unique index: the live (tenant, subject, role) triple already exists
            throw new ConflictException("assignment already exists: subject '" + command.subject() + "', role '"
                    + command.role() + "' in tenant '" + command.tenantCode() + "'");
        }
    }

    @Transactional(readOnly = true)
    public Page<AssignmentView> search(String subject, String tenantCode, String role, Pageable pageable) {
        return assignments.search(subject, tenantCode, role, pageable).map(RoleAssignmentService::toView);
    }

    /** Logically deletes an assignment (retention, not removal). */
    @Transactional
    public void delete(Long id) {
        RoleAssignment assignment = assignments.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("RoleAssignment", "id", id));
        assignment.markDeleted(changeContext.author(), changeContext.messageOr(() -> "delete assignment"));
    }

    /** Restores a logically-deleted assignment; conflicts with a re-granted live row. */
    @Transactional
    public AssignmentView restore(Long id) {
        RoleAssignment assignment = assignments.findByIdIncludingDeleted(id)
                .orElseThrow(() -> ResourceNotFoundException.of("RoleAssignment", "id", id));
        if (assignment.isDeleted()) {
            assignment.restore();
            try {
                assignments.flush();
            } catch (DataIntegrityViolationException ex) {
                throw new ConflictException("cannot restore assignment: a live row already grants role '"
                        + assignment.getRole() + "' to subject '" + assignment.getSubject() + "' in tenant '"
                        + assignment.getTenantCode() + "'");
            }
        }
        return toView(assignment);
    }

    private void requireActiveTenant(String tenantCode) {
        if (!tenants.existsByCodeAndEnabledTrue(tenantCode)) {
            throw new ValidationException("tenant '" + tenantCode + "' is disabled or unknown");
        }
    }

    private static AssignmentView toView(RoleAssignment assignment) {
        return new AssignmentView(assignment.getId(), assignment.getSubject(), assignment.getTenantCode(),
                assignment.getRole(), assignment.getCreatedAt(), assignment.getUpdatedAt());
    }
}
