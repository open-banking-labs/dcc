package cn.org.openbanking.dcc.application.tenant;

import java.time.Instant;
import java.util.List;

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

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use cases for managing the authorization directory (role assignments). An
 * assignment grants one role to a subject inside one tenant; in directory mode it
 * is unioned with the token's claim roles before bundle expansion, so deployments
 * assign names declared under {@code dcc.security.role-bundles}. Audited/retained
 * like every other row and managed platform-level (not tenant-scoped).
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
            ScopeType scopeType, String scopeCode, String grantedBy, boolean confirmationRequired,
            String confirmedBy, Instant confirmedAt,
            Instant createdAt, Instant updatedAt) {
    }

    private final RoleAssignmentRepository assignments;
    private final TenantRepository tenants;
    private final TenantEnvironmentRepository environments;
    private final TenantApplicationRepository applications;
    private final ChangeContext changeContext;
    /**
     * Roles whose grants need a second person (ADR 0005 §D10). Read here rather than passed in from
     * the controllers on purpose: dual control is an invariant of granting, not a per-endpoint
     * decision, so every surface (REST, MCP, a seeder) inherits it.
     */
    private final List<String> dualControlRoles;

    public RoleAssignmentService(RoleAssignmentRepository assignments, TenantRepository tenants,
            TenantEnvironmentRepository environments, TenantApplicationRepository applications,
            ChangeContext changeContext,
            @Value("${dcc.security.dual-control-roles:DCC_PLATFORM_ADMIN,DCC_TENANT_ADMIN}")
            List<String> dualControlRoles) {
        this.assignments = assignments;
        this.tenants = tenants;
        this.environments = environments;
        this.applications = applications;
        this.changeContext = changeContext;
        this.dualControlRoles = dualControlRoles == null ? List.of() : List.copyOf(dualControlRoles);
    }

    /**
     * Creates a grant. A privileged role (see {@code dcc.security.dual-control-roles}) is written
     * and audited immediately but confers nothing until a <em>different</em> subject confirms it.
     */
    @Transactional
    public AssignmentView create(CreateCommand command) {
        requireActiveTenant(command.tenantCode());
        boolean confirmationRequired = dualControlRoles.contains(command.role());
        ScopeType scopeType = command.scopeType() == null ? ScopeType.TENANT : command.scopeType();
        String scopeCode = normalizeScopeCode(scopeType, command.scopeCode(), command.tenantCode());
        RoleAssignment assignment = new RoleAssignment(command.subject(), command.tenantCode(), command.role(),
                scopeType, scopeCode);
        assignment.setGrantedBy(changeContext.boundAuthor());
        assignment.setConfirmationRequired(confirmationRequired);
        try {
            return toView(assignments.saveAndFlush(assignment));
        } catch (DataIntegrityViolationException ex) {
            // partial unique index: this live (tenant, subject, role, scope) already exists
            throw new ConflictException("assignment already exists: subject '" + command.subject() + "', role '"
                    + command.role() + "' in tenant '" + command.tenantCode() + "'");
        }
    }

    /**
     * Confirms a pending privileged grant (dual control): the confirmer must be a different subject
     * from the one that created the grant.
     */
    @Transactional
    public AssignmentView confirm(Long id) {
        return confirmAssignment(id, null);
    }

    /** Confirms a pending privileged grant of <em>this</em> tenant. */
    @Transactional
    public AssignmentView confirmInTenant(String tenantCode, Long id) {
        return confirmAssignment(id, tenantCode);
    }

    private AssignmentView confirmAssignment(Long id, String tenantCode) {
        RoleAssignment assignment = assignments.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("RoleAssignment", "id", id));
        if (tenantCode != null) {
            requireSameTenant(tenantCode, assignment);
        }
        if (!assignment.isConfirmationRequired()) {
            throw new ValidationException("role '" + assignment.getRole() + "' does not require confirmation");
        }
        if (assignment.getConfirmedBy() != null) {
            throw new ConflictException("assignment " + id + " is already confirmed by '"
                    + assignment.getConfirmedBy() + "'");
        }
        String confirmer = changeContext.boundAuthor();
        if (confirmer == null) {
            throw new ValidationException("confirmation requires an authenticated caller");
        }
        if (confirmer.equals(assignment.getGrantedBy())) {
            throw new ConflictException("dual control: '" + confirmer
                    + "' created this grant and cannot also confirm it");
        }
        assignment.confirm(confirmer, Instant.now());
        return toView(assignment);
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

    // ------------------------------------------------- tenant-scoped (tenant administrator, ADR 0005 §D5)

    /**
     * Logically deletes a grant of <em>this</em> tenant. A row belonging to another tenant is
     * reported as not found rather than forbidden, so the endpoint cannot be used to probe ids.
     */
    @Transactional
    public void deleteInTenant(String tenantCode, Long id) {
        RoleAssignment assignment = requireInTenant(tenantCode, id);
        assignment.markDeleted(changeContext.author(), changeContext.messageOr(() -> "delete assignment"));
    }

    /** Restores a logically-deleted grant of this tenant; conflicts with a re-granted live row. */
    @Transactional
    public AssignmentView restoreInTenant(String tenantCode, Long id) {
        RoleAssignment assignment = assignments.findByIdIncludingDeleted(id)
                .orElseThrow(() -> ResourceNotFoundException.of("RoleAssignment", "id", id));
        requireSameTenant(tenantCode, assignment);
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

    private RoleAssignment requireInTenant(String tenantCode, Long id) {
        RoleAssignment assignment = assignments.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("RoleAssignment", "id", id));
        requireSameTenant(tenantCode, assignment);
        return assignment;
    }

    private void requireSameTenant(String tenantCode, RoleAssignment assignment) {
        if (tenantCode == null || !tenantCode.equals(assignment.getTenantCode())) {
            throw ResourceNotFoundException.of("RoleAssignment", "id", assignment.getId());
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
                assignment.getGrantedBy(), assignment.isConfirmationRequired(), assignment.getConfirmedBy(),
                assignment.getConfirmedAt(), assignment.getCreatedAt(), assignment.getUpdatedAt());
    }
}
