package cn.org.openbanking.dcc.core.tenant.repository;

import java.util.List;
import java.util.Optional;

import cn.org.openbanking.dcc.core.tenant.RoleAssignment;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence for {@link RoleAssignment}. Directory rows are platform-global. */
public interface RoleAssignmentRepository
        extends JpaRepository<RoleAssignment, Long>, JpaSpecificationExecutor<RoleAssignment> {

    /** @return all live roles granted to a subject inside one tenant. */
    List<RoleAssignment> findAllBySubjectAndTenantCode(String subject, String tenantCode);

    /**
     * Filtered, paged search, newest first; any parameter may be {@code null}.
     *
     * <p>Built with Criteria rather than {@code (:param is null or …)} JPQL: PostgreSQL
     * cannot infer the SQL type of a null untyped parameter (see
     * {@code AuditLogRepository} for the same rationale).
     */
    default Page<RoleAssignment> search(String subject, String tenantCode, String role, Pageable pageable) {
        return findAll(filter(subject, tenantCode, role), pageable);
    }

    /** Loads an assignment including logically-deleted ones so it can be restored. */
    @Query(value = "select * from role_assignment where id = :id", nativeQuery = true)
    Optional<RoleAssignment> findByIdIncludingDeleted(@Param("id") Long id);

    private Specification<RoleAssignment> filter(String subject, String tenantCode, String role) {
        return (root, query, builder) -> {
            java.util.List<jakarta.persistence.criteria.Predicate> parts = new java.util.ArrayList<>();
            if (subject != null) {
                parts.add(builder.equal(root.get("subject"), subject));
            }
            if (tenantCode != null) {
                parts.add(builder.equal(root.get("tenantCode"), tenantCode));
            }
            if (role != null) {
                parts.add(builder.equal(root.get("role"), role));
            }
            return builder.and(parts.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }
}
