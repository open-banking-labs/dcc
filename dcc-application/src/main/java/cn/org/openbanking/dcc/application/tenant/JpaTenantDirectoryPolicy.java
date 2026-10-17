package cn.org.openbanking.dcc.application.tenant;

import java.util.Set;

import cn.org.openbanking.dcc.core.tenant.RoleAssignment;
import cn.org.openbanking.dcc.core.tenant.repository.RoleAssignmentRepository;
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

    public JpaTenantDirectoryPolicy(TenantMembershipRepository memberships, RoleAssignmentRepository assignments) {
        this.memberships = memberships;
        this.assignments = assignments;
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
        return assignments.findAllBySubjectAndTenantCode(subject, tenantId).stream()
                .map(RoleAssignment::getRole)
                .collect(java.util.stream.Collectors.toSet());
    }
}
