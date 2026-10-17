package cn.org.openbanking.dcc.core.common.entity;

import java.time.Instant;
import java.util.Map;

import cn.org.openbanking.dcc.core.common.audit.AuditListener;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Transient;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Common base for every persistent entity: a surrogate identifier, creation/update
 * timestamps and the retention (logical-deletion) marker.
 *
 * <p>Bank-grade compliance forbids physically deleting business records, so nothing is
 * ever removed via {@code delete}; a row is marked with {@link #markDeleted(String, String)}
 * instead and stays retrievable for the retention period. {@link SQLRestriction} keeps
 * logically-deleted rows out of every default view, while {@link AuditListener} records
 * an append-only audit trail of every create, update and delete for this row.
 *
 * <p>Entities that belong to a tenant additionally extend {@link TenantScopedEntity},
 * which adds the {@code tenant_id} row-level discriminator.
 */
@MappedSuperclass
@EntityListeners(AuditListener.class)
@SQLRestriction("deleted_at is null")
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // ---------------------------------------------------------------- retention

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by", length = 128)
    private String deletedBy;

    @Column(name = "deletion_reason", length = 512)
    private String deletionReason;

    /**
     * Pre-mutation snapshot captured by {@link AuditListener} on load, used to tell an
     * ordinary update apart from a logical delete and to record the before-image. It is
     * framework-internal state and is never persisted.
     */
    @Transient
    private transient Map<String, Object> auditBaseline;

    public Long getId() {
        return id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public String getDeletedBy() {
        return deletedBy;
    }

    public String getDeletionReason() {
        return deletionReason;
    }

    /** @return true when this row has been logically deleted. */
    public boolean isDeleted() {
        return deletedAt != null;
    }

    /**
     * Marks this row as logically deleted and records who removed it and why. The row
     * is kept; only the retention marker changes. Memoised: a second call updates the
     * actor/reason but keeps the original deletion instant.
     */
    public void markDeleted(String actor, String reason) {
        if (deletedAt == null) {
            this.deletedAt = Instant.now();
        }
        this.deletedBy = actor;
        this.deletionReason = reason;
    }

    /** Clears the retention marker, bringing this row back into the default view. */
    public void restore() {
        this.deletedAt = null;
        this.deletedBy = null;
        this.deletionReason = null;
    }

    /** @return the pre-mutation snapshot captured on load, or {@code null}. */
    public Map<String, Object> getAuditBaseline() {
        return auditBaseline;
    }

    /** Framework hook used by {@link AuditListener} to capture the before-image. */
    public void setAuditBaseline(Map<String, Object> auditBaseline) {
        this.auditBaseline = auditBaseline;
    }
}
