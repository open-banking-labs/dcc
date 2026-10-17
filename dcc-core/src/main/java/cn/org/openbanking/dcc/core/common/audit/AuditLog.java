package cn.org.openbanking.dcc.core.common.audit;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One append-only entry in the audit trail: who changed which row, when, why, and the
 * before/after images. Written for every create, update and delete by {@link AuditListener}
 * via {@link AuditRecorder}.
 *
 * <p>It deliberately does <em>not</em> extend
 * {@link cn.org.openbanking.dcc.core.common.entity.BaseEntity}: the audit trail itself is
 * immutable and must never be audited, restricted or logically deleted, and it carries no
 * {@code created_at} / {@code updated_at}. {@code hash} chains to {@code previous_hash}
 * (the previous entry for the same entity) so a tampered or removed entry breaks the chain.
 * {@code retention_until} freezes the compliance cutoff at write time ({@code dcc.audit.retention-years});
 * UPDATE/DELETE on this table are revoked at the database level (migration) on top of the
 * application never issuing them.
 */
@Entity
@Table(name = "audit_log",
        indexes = {
                // Tenant-scoped: every read path (history, search, chain lookup) filters by
                // tenant first, so the isolation lives in the index. The partial index for
                // platform-level rows (tenant_id is null) is created by migration only.
                @Index(name = "ix_audit_tenant_entity", columnList = "tenant_id,entity_type,entity_id,id"),
                @Index(name = "ix_audit_tenant_time", columnList = "tenant_id,occurred_at"),
                @Index(name = "ix_audit_tenant_actor", columnList = "tenant_id,actor")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Owning tenant, or {@code null} for platform-level rows (e.g. {@code Tenant}). */
    @Column(name = "tenant_id", length = 64)
    private String tenantId;

    @Column(name = "entity_type", nullable = false, length = 64)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Column(name = "entity_code", length = 128)
    private String entityCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 32)
    private AuditAction action;

    @Column(name = "actor", nullable = false, length = 128)
    private String actor;

    @Column(name = "reason", length = 512)
    private String reason;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "changed_fields")
    private String changedFields;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "before_state")
    private String beforeState;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "after_state")
    private String afterState;

    @Column(name = "trace_id", length = 64)
    private String traceId;

    @Column(name = "previous_hash", length = 71)
    private String previousHash;

    @Column(name = "hash", nullable = false, length = 71)
    private String hash;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    /**
     * Compliance cutoff, frozen at write time from {@code dcc.audit.retention-years}. The
     * trail is never purged by the application; the column exists so the retention position
     * of every entry is auditable and reportable even if the configured policy changes.
     */
    @Column(name = "retention_until", nullable = false)
    private Instant retentionUntil;

    public AuditLog(String tenantId, String entityType, Long entityId, String entityCode, AuditAction action,
            String actor, String reason, String changedFields, String beforeState, String afterState,
            String traceId, String previousHash, String hash, Instant occurredAt, Instant retentionUntil) {
        this.tenantId = tenantId;
        this.entityType = entityType;
        this.entityId = entityId;
        this.entityCode = entityCode;
        this.action = action;
        this.actor = actor;
        this.reason = reason;
        this.changedFields = changedFields;
        this.beforeState = beforeState;
        this.afterState = afterState;
        this.traceId = traceId;
        this.previousHash = previousHash;
        this.hash = hash;
        this.occurredAt = occurredAt;
        this.retentionUntil = retentionUntil;
    }
}
