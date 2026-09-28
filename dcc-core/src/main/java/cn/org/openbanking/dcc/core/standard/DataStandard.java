package cn.org.openbanking.dcc.core.standard;

import cn.org.openbanking.dcc.core.common.entity.TenantScopedEntity;
import cn.org.openbanking.dcc.core.common.tenant.TenantFilter;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import org.hibernate.annotations.Filter;

/**
 * Master record of a data standard (数标).
 *
 * <p>Follows the "master record + version records" model: this row holds identity
 * and scope (tenant + environment + application), the current version pointer and
 * the lifecycle status; the versioned content lives in {@link DataStandardVersion}
 * snapshots.
 *
 * <p>{@code code} (变量名) and the scope columns are immutable: a standard is
 * referenced by its code, so renaming is modelled as a new standard rather than a
 * content change. {@code category} is classification metadata and is <em>not</em>
 * versioned.
 */
@Entity
@Table(name = "data_standard",
        uniqueConstraints = @UniqueConstraint(name = "uk_standard_scope_code",
                columnNames = {"tenant_id", "environment_id", "application_id", "code"}),
        indexes = {
                @Index(name = "ix_standard_scope", columnList = "tenant_id,environment_id,application_id"),
                @Index(name = "ix_standard_category", columnList = "tenant_id,category")
        })
@Filter(name = TenantFilter.NAME, condition = "tenant_id = :tenantId")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DataStandard extends TenantScopedEntity {

    @Column(name = "environment_id", nullable = false, updatable = false)
    private Long environmentId;

    @Column(name = "application_id", nullable = false, updatable = false)
    private Long applicationId;

    @Column(name = "code", nullable = false, length = 128, updatable = false)
    private String code;

    @Column(name = "category", length = 64)
    private String category;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private StandardStatus status;

    @Column(name = "current_version", nullable = false, length = 32)
    private String currentVersion;

    @Column(name = "current_version_id")
    private Long currentVersionId;

    public DataStandard(Long environmentId, Long applicationId, String code, String category,
            StandardStatus status, String currentVersion) {
        this.environmentId = environmentId;
        this.applicationId = applicationId;
        this.code = code;
        this.category = category;
        this.status = status;
        this.currentVersion = currentVersion;
    }

    /** Validates and applies a lifecycle transition. */
    public void transitionTo(StandardStatus target) {
        status.requireTransitionTo(target);
        this.status = target;
    }

    /** Points the master at a newly created version snapshot. */
    public void pointToVersion(Long versionId, String version) {
        this.currentVersionId = versionId;
        this.currentVersion = version;
    }
}
