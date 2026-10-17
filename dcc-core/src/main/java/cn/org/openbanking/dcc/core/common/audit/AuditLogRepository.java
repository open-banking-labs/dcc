package cn.org.openbanking.dcc.core.common.audit;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.criteria.Predicate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Read/append access to the audit trail. It is append-only in practice: only the audit
 * machinery inserts rows and nothing updates or deletes them.
 */
public interface AuditLogRepository extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {

    /** @return the latest entry for a tenant's row, used to continue the hash chain. */
    Optional<AuditLog> findTopByTenantIdAndEntityTypeAndEntityIdOrderByIdDesc(String tenantId, String entityType,
            Long entityId);

    /** @return the latest chain entry for a platform-level row ({@code tenant_id is null}). */
    Optional<AuditLog> findTopByEntityTypeAndEntityIdAndTenantIdIsNullOrderByIdDesc(String entityType, Long entityId);

    /** @return the full history of one row, oldest first. */
    List<AuditLog> findByTenantIdAndEntityTypeAndEntityIdOrderByIdAsc(String tenantId, String entityType,
            Long entityId);

    /**
     * Filtered, paged search; newest entry first. Any of the filter parameters may be
     * {@code null} to mean "no constraint".
     *
     * <p>Built with Criteria rather than {@code (:param is null or …)} JPQL: PostgreSQL
     * cannot infer the SQL type of a null untyped parameter, which breaks the temporal
     * and enum filters at binding time.
     */
    default Page<AuditLog> search(String tenantId, String entityType, Long entityId, String actor,
            AuditAction action, Instant from, Instant to, Pageable pageable) {
        return findAll(filter(tenantId, entityType, entityId, actor, action, from, to), pageable);
    }

    /** @return how many entries are past their compliance cutoff (retention sweep report). */
    long countByRetentionUntilBefore(Instant when);

    private Specification<AuditLog> filter(String tenantId, String entityType, Long entityId, String actor,
            AuditAction action, Instant from, Instant to) {
        return (root, query, builder) -> {
            List<Predicate> parts = new ArrayList<>();
            parts.add(builder.equal(root.get("tenantId"), tenantId));
            if (entityType != null) {
                parts.add(builder.equal(root.get("entityType"), entityType));
            }
            if (entityId != null) {
                parts.add(builder.equal(root.get("entityId"), entityId));
            }
            if (actor != null) {
                parts.add(builder.equal(root.get("actor"), actor));
            }
            if (action != null) {
                parts.add(builder.equal(root.get("action"), action));
            }
            if (from != null) {
                parts.add(builder.greaterThanOrEqualTo(root.get("occurredAt"), from));
            }
            if (to != null) {
                parts.add(builder.lessThanOrEqualTo(root.get("occurredAt"), to));
            }
            return builder.and(parts.toArray(Predicate[]::new));
        };
    }
}
