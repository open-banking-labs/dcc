package cn.org.openbanking.dcc.core.common.audit;

import java.time.Instant;

/**
 * One captured change, buffered in memory until the surrounding transaction is about to
 * commit (see {@link AuditRecorder}). Snapshots are already serialised to JSON strings so
 * the entry holds no live entity references.
 */
public record AuditEntry(
        String tenantId,
        String entityType,
        Long entityId,
        String entityCode,
        AuditAction action,
        String actor,
        String reason,
        String traceId,
        String changedFields,
        String beforeState,
        String afterState,
        Instant occurredAt) {

    /** Canonical form hashed (chained to the previous entry) for tamper detection. Public so
     * the read side ({@code AuditService.verify}) can recompute and compare the same chain. */
    public String canonical(String previousHash) {
        return (previousHash == null ? "" : previousHash)
                + "|" + entityType
                + "|" + entityId
                + "|" + action
                + "|" + occurredAt
                + "|" + (beforeState == null ? "" : beforeState)
                + "|" + (afterState == null ? "" : afterState);
    }
}
