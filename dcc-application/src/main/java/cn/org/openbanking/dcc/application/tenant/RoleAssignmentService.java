package cn.org.openbanking.dcc.application.tenant;

import java.time.Instant;

import cn.org.openbanking.dcc.application.common.ChangeContext;
import cn.org.openbanking.dcc.core.common.error.ConflictException;
import cn.org.openbanking.dcc.core.common.error.ResourceNotFoundException;
import cn.org.openbanking.dcc.core.common.error.ValidationException;
import cn.org.openbanking.dcc.core.tenant.RoleAssignment;
import cn.org.openbanking.dcc.core.tenant.ScopeType;
import cn.org.openbanking.dcc.core.tenant.repository.RoleAssignmentRepository;
import cn.org.openbanking.dcc.core.tenant.repository.TenantApplicationRepository;
import cn.org.openbanking.dcc.core.tenant.repository.TenantEnvironmentRepository;
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
            @NotBlank @Size(max = 64) String role,
            ScopeType scopeType,
            @Size(max = 64) String scopeCode) {

        public CreateCommand(String subject, String tenantCode, String role) {
            this(subject, tenantCode, role, ScopeType.TENANT, null);
        }
    }

    public record AssignmentView(Long id, String subject, String tenantCode, String role,
            ScopeType scopeType, String scopeCode,
            Instant createdAt, Instant updatedAt) {
    }

    private final RoleAssignmentRepository assignments;
    private final TenantRepository tenants;
    private final TenantEnvironmentRepository environments;
    private final TenantApplicationRepository applications;
    private final ChangeContext changeContext;

    public RoleAssignmentService(RoleAssignmentRepository assignments, TenantRepository tenants,
            TenantEnvironmentRepository environments, TenantApplicationRepository applications,
            ChangeContext changeContext) {
        this.assignments = assignments;
        this.tenants = tenants;
        this.environments = environments;
        this.applications = applications;
        this.changeContext = changeContext;
    }

    @Transactional
    public AssignmentView create(CreateCommand command) {
        requireActiveTenant(command.tenantCode());
        ScopeType scopeType = command.scopeType() == null ? ScopeType.TENANT : command.scopeType();
        String scopeCode = normalizeScopeCode(scopeType, command.scopeCode(), command.tenantCode());
        try {
            return toView(assignments.saveAndFlush(
                    new RoleAssignment(command.subject(), command.tenantCode(), command.role(), scopeType, scopeCode)));
        } catch (DataIntegrityViolationException ex) {
            // partial unique index: this live (tenant, subject, role, scope) already exists
            throw new ConflictException("assignment already exists: subject '" + command.subject() + "', role '"
                    + command.role() + "' in tenant '" + command.tenantCode() + "'");
        }
    }

    @Transactional(readOnly = true)
    public Page<AssignmentView> search(String subject, String tenantCode, String role,
            ScopeType scopeType, String scopeCode, Pageable pageable) {
        return assignments.search(subject, tenantCode, role, scopeType, scopeCode, pageable)
                .map(RoleAssignmentService::toView);
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

    private String normalizeScopeCode(ScopeType scopeType, String scopeCode, String tenantCode) {
        if (scopeType == ScopeType.TENANT) {
            if (scopeCode != null && !scopeCode.isBlank()) {
                throw new ValidationException("scopeCode must be empty for TENANT assignments");
            }
            return null;
        }
        if (scopeCode == null || scopeCode.isBlank()) {
            throw new ValidationException("scopeCode is required for " + scopeType + " assignments");
        }
        String normalized = scopeCode.trim();
        boolean exists = switch (scopeType) {
            case TENANT -> true;
            case ENVIRONMENT -> environments.existsByTenantIdAndCode(tenantCode, normalized);
            case APPLICATION -> applications.existsByTenantIdAndCode(tenantCode, normalized);
        };
        if (!exists) {
            throw new ValidationException(scopeType.name().toLowerCase() + " '" + normalized
                    + "' is unknown in tenant '" + tenantCode + "'");
        }
        return normalized;
    }

    private static AssignmentView toView(RoleAssignment assignment) {
        return new AssignmentView(assignment.getId(), assignment.getSubject(), assignment.getTenantCode(),
                assignment.getRole(), assignment.getScopeType(), assignment.getScopeCode(),
                assignment.getCreatedAt(), assignment.getUpdatedAt());
    }
}
