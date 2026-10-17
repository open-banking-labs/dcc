package cn.org.openbanking.dcc.core.common.audit;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import cn.org.openbanking.dcc.core.common.entity.BaseEntity;
import cn.org.openbanking.dcc.core.common.entity.TenantScopedEntity;
import cn.org.openbanking.dcc.core.common.tenant.CurrentTenant;
import cn.org.openbanking.dcc.core.version.ContentHash;

import jakarta.persistence.EntityManager;

import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Buffers audit entries produced by {@link AuditListener} and writes them just before the
 * surrounding transaction commits.
 *
 * <p>Why buffer instead of writing straight from the JPA callbacks? The callbacks run
 * <em>inside</em> a Hibernate flush; persisting a new row there would re-enter the flush.
 * Instead entries are collected on the current thread and flushed from a
 * {@link TransactionSynchronization#beforeCommit(boolean)} callback: before draining the
 * buffer that callback forces an {@link EntityManager#flush()}, so the callbacks that fire
 * at commit time have already run and no change can escape the trail. The audit rows are
 * then committed atomically with the change that produced them — a rolled-back change
 * leaves no entry, and a committed change can never be missing one.
 *
 * <p>Multiple callbacks for the same row within one transaction (Hibernate may flush
 * several times) are coalesced into a single entry carrying the earliest before-image and
 * the latest after-image, i.e. the net effect of the transaction on that row.
 */
@Component
public class AuditRecorder {

    private static final Logger log = LoggerFactory.getLogger(AuditRecorder.class);

    private static final ThreadLocal<LinkedHashMap<String, Pending>> BUFFER =
            ThreadLocal.withInitial(LinkedHashMap::new);
    private static final ThreadLocal<Boolean> REGISTERED = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private static final String SYSTEM_ACTOR = "system";

    private final ObjectProvider<AuditLogRepository> repositoryProvider;
    private final ObjectProvider<AuditIdentityProvider> identityProvider;
    private final ObjectProvider<EntityManager> entityManagerProvider;
    private final ObjectProvider<AuditProperties> auditPropertiesProvider;

    public AuditRecorder(ObjectProvider<AuditLogRepository> repositoryProvider,
            ObjectProvider<AuditIdentityProvider> identityProvider,
            ObjectProvider<EntityManager> entityManagerProvider,
            ObjectProvider<AuditProperties> auditPropertiesProvider) {
        this.repositoryProvider = repositoryProvider;
        this.identityProvider = identityProvider;
        this.entityManagerProvider = entityManagerProvider;
        this.auditPropertiesProvider = auditPropertiesProvider;
    }

    /** A coalesced change for one row within the current transaction. */
    private static final class Pending {
        private final String tenantId;
        private final String entityType;
        private final Long entityId;
        private final AuditAction action;
        private final String actor;
        private final String reason;
        private final String traceId;
        private final Instant occurredAt;
        private Map<String, Object> before;
        private Map<String, Object> after;

        private Pending(String tenantId, String entityType, Long entityId, AuditAction action, String actor,
                String reason, String traceId, Instant occurredAt, Map<String, Object> before,
                Map<String, Object> after) {
            this.tenantId = tenantId;
            this.entityType = entityType;
            this.entityId = entityId;
            this.action = action;
            this.actor = actor;
            this.reason = reason;
            this.traceId = traceId;
            this.occurredAt = occurredAt;
            this.before = before;
            this.after = after;
        }
    }

    /**
     * Ensures this transaction will flush the audit trail at commit time. Called from the
     * entity callbacks; registering on load means a transaction that only loads and then
     * modifies a row (so no insert callback fires early) still registers in time.
     */
    public void ensureRegistered() {
        registerSynchronization();
    }

    /**
     * Captures one change. Called from the JPA entity callbacks; it only buffers the entry
     * and arranges for the buffered entries to be persisted at commit time.
     */
    public void record(BaseEntity entity, AuditAction action, Map<String, Object> before, Map<String, Object> after) {
        Map<String, Object> reference = after != null ? after : before;
        String entityType = Hibernate.getClass(entity).getSimpleName();
        String key = entityType + "#" + entity.getId();
        BUFFER.get().merge(key,
                new Pending(tenantId(entity, reference), entityType, entity.getId(), action, actor(), reason(),
                        MDC.get("traceId"), Instant.now(), before, after),
                AuditRecorder::coalesce);
        registerSynchronization();
    }

    /** Keeps the earliest before-image and the latest after-image, i.e. the net change. */
    private static Pending coalesce(Pending first, Pending latest) {
        first.after = latest.after != null ? latest.after : first.after;
        if (first.before == null) {
            first.before = latest.before;
        }
        return first;
    }

    private void registerSynchronization() {
        if (!TransactionSynchronizationManager.isSynchronizationActive() || Boolean.TRUE.equals(REGISTERED.get())) {
            return;
        }
        REGISTERED.set(Boolean.TRUE);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void beforeCommit(boolean readOnly) {
                if (!readOnly) {
                    writeBuffered();
                }
            }

            @Override
            public void afterCompletion(int status) {
                BUFFER.get().clear();
                REGISTERED.set(Boolean.FALSE);
            }
        });
    }

    private void writeBuffered() {
        // The entity callbacks that fill the buffer fire during a Hibernate flush, which
        // would otherwise run at commit — after this callback. Flushing here first
        // guarantees every pending change has been captured before we drain the buffer.
        EntityManager entityManager = entityManagerProvider.getIfAvailable();
        if (entityManager != null && entityManager.isOpen()) {
            entityManager.flush();
        }
        Map<String, Pending> buffered = BUFFER.get();
        if (buffered.isEmpty()) {
            return;
        }
        List<Pending> batch = new ArrayList<>(buffered.values());
        buffered.clear();

        AuditLogRepository repository = repositoryProvider.getObject();
        int retentionYears = retentionYears();
        for (Pending entry : batch) {
            try {
                AuditEntry audit = toEntry(entry);
                String previousHash = tenantScopedChainTail(audit);
                String hash = ContentHash.sha256(audit.canonical(previousHash));
                repository.save(new AuditLog(audit.tenantId(), audit.entityType(), audit.entityId(),
                        audit.entityCode(), audit.action(), audit.actor(), audit.reason(),
                        audit.changedFields(), audit.beforeState(), audit.afterState(), audit.traceId(),
                        previousHash, hash, audit.occurredAt(), retentionUntil(audit.occurredAt(), retentionYears)));
            } catch (RuntimeException ex) {
                // Auditing must not silently swallow a change; surface it so the whole
                // transaction (and therefore the unaudited change) rolls back.
                throw new IllegalStateException("failed to persist audit entry for "
                        + entry.entityType + "#" + entry.entityId, ex);
            }
        }
        log.debug("recorded {} audit entr{}", batch.size(), batch.size() == 1 ? "y" : "ies");
    }

    /** Chain tail lookup is tenant-scoped so the isolation lives in the new composite index.
     *  Platform-level rows ({@code Tenant}) have no tenant and chain on {@code tenant_id is null}. */
    private String tenantScopedChainTail(AuditEntry audit) {
        AuditLogRepository repository = repositoryProvider.getObject();
        return (audit.tenantId() == null
                ? repository.findTopByEntityTypeAndEntityIdAndTenantIdIsNullOrderByIdDesc(audit.entityType(),
                        audit.entityId())
                : repository.findTopByTenantIdAndEntityTypeAndEntityIdOrderByIdDesc(audit.tenantId(),
                        audit.entityType(), audit.entityId()))
                .map(AuditLog::getHash)
                .orElse(null);
    }

    /** UTC calendar-years cutoff, frozen per entry so policy changes never rewrite history. */
    private static Instant retentionUntil(Instant occurredAt, int years) {
        return occurredAt.atZone(java.time.ZoneOffset.UTC).plusYears(years).toInstant();
    }

    private int retentionYears() {
        AuditProperties properties = auditPropertiesProvider.getIfAvailable();
        return (properties == null ? new AuditProperties() : properties).getRetentionYears();
    }

    private AuditEntry toEntry(Pending pending) {        Map<String, Object> reference = pending.after != null ? pending.after : pending.before;
        return new AuditEntry(
                pending.tenantId,
                pending.entityType,
                pending.entityId,
                businessCode(reference),
                pending.action,
                pending.actor,
                pending.reason,
                pending.traceId,
                AuditJson.write(AuditSnapshot.changes(pending.before, pending.after)),
                pending.before == null ? null : AuditJson.write(pending.before),
                pending.after == null ? null : AuditJson.write(pending.after),
                pending.occurredAt);
    }

    private String tenantId(BaseEntity entity, Map<String, Object> reference) {
        if (entity instanceof TenantScopedEntity scoped) {
            return scoped.getTenantId();
        }
        if (reference != null && reference.get("tenantId") instanceof String tenantId) {
            return tenantId;
        }
        return CurrentTenant.get();
    }

    private static String businessCode(Map<String, Object> reference) {
        if (reference == null) {
            return null;
        }
        Object code = reference.get("code");
        if (code == null) {
            code = reference.get("interfaceNo");
        }
        return code == null ? null : code.toString();
    }

    private String actor() {
        AuditIdentityProvider provider = identityProvider.getIfAvailable();
        String actor = provider == null ? null : provider.actor();
        return actor == null || actor.isBlank() ? SYSTEM_ACTOR : actor;
    }

    private String reason() {
        AuditIdentityProvider provider = identityProvider.getIfAvailable();
        return provider == null ? null : provider.reason();
    }
}
