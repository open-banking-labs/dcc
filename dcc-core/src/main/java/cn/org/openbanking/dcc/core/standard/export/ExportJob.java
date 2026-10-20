package cn.org.openbanking.dcc.core.standard.export;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import cn.org.openbanking.dcc.core.standard.StandardStatus;

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
import lombok.Setter;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Queue row for an asynchronous standards Excel export.
 *
 * <p>Deliberately <em>not</em> a {@code BaseEntity}: an export job is operational
 * bookkeeping, not a business record. It therefore carries no retention columns, is
 * excluded from the audit trail, and every repository query states its tenant scope
 * explicitly instead of relying on the Hibernate tenant filter.
 */
@Entity
@Table(name = "export_job",
        indexes = @Index(name = "ix_export_job_tenant_status", columnList = "tenant_id,status"))
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExportJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, updatable = false, length = 64)
    private String tenantId;

    @Column(name = "requested_by", length = 128)
    private String requestedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ExportJobStatus status;

    @Column(name = "environment_id", nullable = false, updatable = false)
    private Long environmentId;

    @Column(name = "application_id", nullable = false, updatable = false)
    private Long applicationId;

    /** The raw keyword the user asked for, kept so the job is reproducible. */
    @Column(name = "keyword", length = 128)
    private String keyword;

    @Column(name = "category", length = 64)
    private String category;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_filter", length = 32)
    private StandardStatus statusFilter;

    /** Comma-separated selected ids; null means "use the filters". */
    @Column(name = "standard_ids", length = 2048)
    private String standardIds;

    @Column(name = "object_key", length = 512)
    private String objectKey;

    @Column(name = "file_name", length = 256)
    private String fileName;

    @Column(name = "row_count")
    private Integer rowCount;

    @Column(name = "error_message", length = 1024)
    private String errorMessage;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public ExportJob(String tenantId, String requestedBy, Long environmentId, Long applicationId,
            String keyword, String category, StandardStatus statusFilter, String standardIds) {
        this.tenantId = tenantId;
        this.requestedBy = requestedBy;
        this.environmentId = environmentId;
        this.applicationId = applicationId;
        this.keyword = keyword;
        this.category = category;
        this.statusFilter = statusFilter;
        this.standardIds = standardIds;
        this.status = ExportJobStatus.PENDING;
    }

    public void markRunning(Instant now) {
        this.status = ExportJobStatus.RUNNING;
        this.startedAt = now;
        this.errorMessage = null;
    }

    public void markReady(String objectKey, String fileName, int rowCount, Instant expiresAt) {
        this.status = ExportJobStatus.READY;
        this.objectKey = objectKey;
        this.fileName = fileName;
        this.rowCount = rowCount;
        this.expiresAt = expiresAt;
        this.finishedAt = Instant.now();
    }

    public void markFailed(String message) {
        this.status = ExportJobStatus.FAILED;
        this.errorMessage = message;
        this.finishedAt = Instant.now();
    }

    public void markExpired() {
        this.status = ExportJobStatus.EXPIRED;
    }

    /** @return the explicitly selected ids, or an empty list when the job uses filters. */
    public List<Long> parsedIds() {
        if (standardIds == null || standardIds.isBlank()) {
            return List.of();
        }
        return Arrays.stream(standardIds.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(value -> {
                    try {
                        return Long.valueOf(value);
                    } catch (NumberFormatException ignored) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .toList();
    }
}
