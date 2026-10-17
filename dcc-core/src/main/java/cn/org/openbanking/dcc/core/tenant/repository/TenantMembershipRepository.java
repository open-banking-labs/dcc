package cn.org.openbanking.dcc.core.tenant.repository;

import java.util.Optional;

import cn.org.openbanking.dcc.core.tenant.TenantMembership;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence for {@link TenantMembership}. Directory rows are platform-global. */
public interface TenantMembershipRepository
        extends JpaRepository<TenantMembership, Long>, JpaSpecificationExecutor<TenantMembership> {

    /** @return whether a live membership links the subject and the tenant. */
    boolean existsBySubjectAndTenantCode(String subject, String tenantCode);

    /**
     * Filtered, paged search, newest first; any parameter may be {@code null}.
     *
     * <p>Built with Criteria rather than {@code (:param is null or …)} JPQL: PostgreSQL
     * cannot infer the SQL type of a null untyped parameter, which breaks filters at
     * binding time (see {@code AuditLogRepository} for the same rationale).
     */
    default Page<TenantMembership> search(String subject, String tenantCode, Pageable pageable) {
        return findAll(filter(subject, tenantCode), pageable);
    }

    /** Loads a membership including logically-deleted ones so it can be restored. */
    @Query(value = "select * from tenant_membership where id = :id", nativeQuery = true)
    Optional<TenantMembership> findByIdIncludingDeleted(@Param("id") Long id);

    private Specification<TenantMembership> filter(String subject, String tenantCode) {
        return (root, query, builder) -> {
            java.util.List<jakarta.persistence.criteria.Predicate> parts = new java.util.ArrayList<>();
            if (subject != null) {
                parts.add(builder.equal(root.get("subject"), subject));
            }
            if (tenantCode != null) {
                parts.add(builder.equal(root.get("tenantCode"), tenantCode));
            }
            return builder.and(parts.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }
}
