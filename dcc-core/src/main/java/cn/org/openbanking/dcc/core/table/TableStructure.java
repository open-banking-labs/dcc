package cn.org.openbanking.dcc.core.table;

import cn.org.openbanking.dcc.core.common.entity.TenantScopedEntity;
import cn.org.openbanking.dcc.core.common.tenant.TenantFilter;
import cn.org.openbanking.dcc.core.standard.StandardStatus;

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
 * Master record of a table structure. {@code code} is the physical table name and
 * is immutable identity; the versioned content lives in {@link TableStructureVersion}.
 */
@Entity
@Table(name = "table_structure",
        uniqueConstraints = @UniqueConstraint(name = "uk_table_scope_code",
                columnNames = {"tenant_id", "environment_id", "application_id", "code"}),
        indexes = @Index(name = "ix_table_scope", columnList = "tenant_id,environment_id,application_id"))
@Filter(name = TenantFilter.NAME, condition = "tenant_id = :tenantId")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TableStructure extends TenantScopedEntity {

    @Column(name = "environment_id", nullable = false, updatable = false)
    private Long environmentId;

    @Column(name = "application_id", nullable = false, updatable = false)
    private Long applicationId;

    @Column(name = "code", nullable = false, length = 128, updatable = false)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private StandardStatus status;

    @Column(name = "current_version", nullable = false, length = 32)
    private String currentVersion;

    @Column(name = "current_version_id")
    private Long currentVersionId;

    public TableStructure(Long environmentId, Long applicationId, String code, StandardStatus status,
            String currentVersion) {
        this.environmentId = environmentId;
        this.applicationId = applicationId;
        this.code = code;
        this.status = status;
        this.currentVersion = currentVersion;
    }

    public void transitionTo(StandardStatus target) {
        status.requireTransitionTo(target);
        this.status = target;
    }

    public void pointToVersion(Long versionId, String version) {
        this.currentVersionId = versionId;
        this.currentVersion = version;
    }
}
