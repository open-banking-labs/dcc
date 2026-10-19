package cn.org.openbanking.dcc.application.tenant;

import java.util.Set;

import cn.org.openbanking.dcc.core.tenant.RoleAssignment;
import cn.org.openbanking.dcc.core.tenant.ScopeType;
import cn.org.openbanking.dcc.core.tenant.repository.RoleAssignmentRepository;
import cn.org.openbanking.dcc.core.tenant.repository.TenantApplicationRepository;
import cn.org.openbanking.dcc.core.tenant.repository.TenantEnvironmentRepository;
import cn.org.openbanking.dcc.core.tenant.repository.TenantMembershipRepository;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * The repository-backed implementation of the authorization directory policy, backed
 * by the {@code tenant_membership} / {@code role_assignment} tables. Lives in the
 * application module (which owns the tenant tables) so both exposure layers consume
 * it without a security -> JPA dependency.
 *
 * <p>Logically-deleted rows are excluded by the entity {@code @SQLRestriction}, so
 * "live membership / live assignment" is just a plain derived query.
 */
@Component
public class JpaTenantDirectoryPolicy implements TenantDirectoryPolicy {

    private final TenantMembershipRepository memberships;
    private final RoleAssignmentRepository assignments;
    private final TenantEnvironmentRepository environments;
    private final TenantApplicationRepository applications;

    public JpaTenantDirectoryPolicy(TenantMembershipRepository memberships, RoleAssignmentRepository assignments,
            TenantEnvironmentRepository environments, TenantApplicationRepository applications) {
        this.memberships = memberships;
        this.assignments = assignments;
        this.environments = environments;
        this.applications = applications;
    }

    @Override
    public boolean isMember(String subject, String tenantId) {
        if (!StringUtils.hasText(subject) || !StringUtils.hasText(tenantId)) {
            return false;
        }
        return memberships.existsBySubjectAndTenantCode(subject, tenantId);
    }

    @Override
    public Set<String> assignedRoles(String subject, String tenantId) {
        if (!StringUtils.hasText(subject) || !StringUtils.hasText(tenantId)) {
            return Set.of();
        }
        return assignments.findAllBySubjectAndTenantCodeAndScopeType(subject, tenantId, ScopeType.TENANT).stream()
                .filter(assignment -> !assignment.isPendingConfirmation())
                .map(RoleAssignment::getRole)
                .collect(java.util.stream.Collectors.toSet());
    }

    @Override
    public Set<String> assignedRoles(String subject, String tenantId, String scopeType,
            String scopeCode, Long targetId) {
        if (!StringUtils.hasText(subject) || !StringUtils.hasText(tenantId)
                || !StringUtils.hasText(scopeType) || !StringUtils.hasText(scopeCode) || targetId == null) {
            return Set.of();
        }
        final ScopeType type;
        try {
            type = ScopeType.valueOf(scopeType);
        } catch (IllegalArgumentException ex) {
            return Set.of();
        }
        boolean matches = switch (type) {
            case TENANT -> false;
            case ENVIRONMENT -> environments.findByTenantIdAndId(tenantId, targetId)
                    .map(environment -> scopeCode.equals(environment.getCode())).orElse(false);
            case APPLICATION -> applications.findByTenantIdAndId(tenantId, targetId)
                    .map(application -> scopeCode.equals(application.getCode())).orElse(false);
        };
        if (!matches) {
            return Set.of();
        }
        return assignments.findAllBySubjectAndTenantCodeAndScopeTypeAndScopeCode(
                        subject, tenantId, type, scopeCode).stream()
                .filter(assignment -> !assignment.isPendingConfirmation())
                .map(RoleAssignment::getRole)
                .collect(java.util.stream.Collectors.toSet());
    }
}
