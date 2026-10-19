package cn.org.openbanking.dcc.application.audit;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import cn.org.openbanking.dcc.core.common.audit.AuditAction;
import cn.org.openbanking.dcc.core.common.audit.AuditEntry;
import cn.org.openbanking.dcc.core.common.audit.AuditLog;
import cn.org.openbanking.dcc.core.common.audit.AuditLogRepository;
import cn.org.openbanking.dcc.core.version.ContentHash;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read access to the append-only audit trail: what changed, who changed it and why, for
 * compliance inspection and "restore from history" workflows.
 *
 * <p>The trail itself is never logically deleted, so it stays readable after the entity it
 * describes has been logically deleted (the entity is filtered from business queries but
 * its history remains).
 */
@Service
@Transactional(readOnly = true)
public class AuditService {

    private final AuditLogRepository auditLog;

    public AuditService(AuditLogRepository auditLog) {
        this.auditLog = auditLog;
    }

    public record AuditView(Long id, String tenantId, String entityType, Long entityId, String entityCode,
            AuditAction action, String actor, String reason, String changedFields, String beforeState,
            String afterState, String traceId, String previousHash, String hash, Instant occurredAt) {
    }

    /** Result of recomputing a row's hash chain: {@code intact} plus where it breaks, if anywhere. */
    public record VerifyView(boolean intact, List<String> problems) {
    }

    /** @return the full, oldest-first history of one row. */
    public List<AuditView> history(String tenantId, String entityType, Long entityId) {
        return auditLog.findByTenantIdAndEntityTypeAndEntityIdOrderByIdAsc(tenantId, entityType, entityId).stream()
                .map(AuditService::toView).toList();
    }

    /** @return matching entries, newest first; any filter may be {@code null}; paged. */
    public Page<AuditView> search(String tenantId, String entityType, Long entityId, String actor,
            AuditAction action, Instant from, Instant to, Pageable pageable) {
        return auditLog
                .search(tenantId, entityType, entityId, actor, action, from, to, pageable)
                .map(AuditService::toView);
    }

    /**
     * @return matching platform-level entries ({@code tenant_id is null}), newest first: tenant
     * admission, directory grants and reference-case changes. These rows belong to no tenant, so
     * the tenant-scoped {@link #search} deliberately cannot return them (ADR 0005 §D6).
     */
    public Page<AuditView> searchPlatform(String entityType, Long entityId, String actor,
            AuditAction action, Instant from, Instant to, Pageable pageable) {
        return auditLog
                .search(null, entityType, entityId, actor, action, from, to, pageable)
                .map(AuditService::toView);
    }

    /**
     * Recomputes the tamper-evident hash chain of one row: every entry's hash must match the
     * hash of its stored canonical form, and every {@code previous_hash} must point at the
     * preceding entry's hash. A missing, reordered or edited entry shows up as a problem.
     */
    public VerifyView verify(String tenantId, String entityType, Long entityId) {
        List<AuditLog> history = auditLog
                .findByTenantIdAndEntityTypeAndEntityIdOrderByIdAsc(tenantId, entityType, entityId);
        List<String> problems = new ArrayList<>();
        String expectedPreviousHash = null;
        for (AuditLog entry : history) {
            if (!Objects.equals(entry.getPreviousHash(), expectedPreviousHash)) {
                problems.add("entry " + entry.getId() + ": previous_hash does not point at its predecessor "
                        + "(entry removed or reordered)");
            }
            String recomputed = ContentHash.sha256(canonical(entry));
            if (!recomputed.equals(entry.getHash())) {
                problems.add("entry " + entry.getId() + ": hash mismatch — stored payload was altered after write");
            }
            expectedPreviousHash = entry.getHash();
        }
        return new VerifyView(problems.isEmpty(), problems);
    }

    /** The exact canonical form that was hashed when the entry was written. */
    private static String canonical(AuditLog entry) {
        return new AuditEntry(entry.getTenantId(), entry.getEntityType(), entry.getEntityId(),
                entry.getEntityCode(), entry.getAction(), entry.getActor(), entry.getReason(),
                entry.getTraceId(), entry.getChangedFields(), entry.getBeforeState(), entry.getAfterState(),
                entry.getOccurredAt())
                .canonical(entry.getPreviousHash());
    }

    private static AuditView toView(AuditLog log) {
        return new AuditView(log.getId(), log.getTenantId(), log.getEntityType(), log.getEntityId(),
                log.getEntityCode(), log.getAction(), log.getActor(), log.getReason(), log.getChangedFields(),
                log.getBeforeState(), log.getAfterState(), log.getTraceId(), log.getPreviousHash(), log.getHash(),
                log.getOccurredAt());
    }
}
