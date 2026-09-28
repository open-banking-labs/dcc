package cn.org.openbanking.dcc.core.migration;

import cn.org.openbanking.dcc.core.common.entity.TenantScopedEntity;
import cn.org.openbanking.dcc.core.common.tenant.TenantFilter;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import org.hibernate.annotations.Filter;

/**
 * A migration order (迁移单): moves one artifact version from a source environment
 * to a target environment, gated by approval. The target environment's version can
 * only come from such a migration, never from direct maintenance.
 */
@Entity
@Table(name = "migration_order",
        indexes = {
                @Index(name = "ix_migration_scope", columnList = "tenant_id,target_environment_id,status"),
                @Index(name = "ix_migration_artifact", columnList = "tenant_id,artifact_type,artifact_id")
        })
@Filter(name = TenantFilter.NAME, condition = "tenant_id = :tenantId")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MigrationOrder extends TenantScopedEntity {

    @Column(name = "source_environment_id", nullable = false, updatable = false)
    private Long sourceEnvironmentId;

    @Column(name = "target_environment_id", nullable = false, updatable = false)
    private Long targetEnvironmentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "artifact_type", nullable = false, updatable = false, length = 32)
    private ArtifactType artifactType;

    @Column(name = "artifact_id", nullable = false, updatable = false)
    private Long artifactId;

    @Column(name = "artifact_code", length = 128)
    private String artifactCode;

    @Column(name = "source_version", length = 32)
    private String sourceVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private MigrationStatus status;

    @Column(name = "note", length = 512)
    private String note;

    @Column(name = "target_artifact_id")
    private Long targetArtifactId;

    @Column(name = "target_version", length = 32)
    private String targetVersion;

    /** The target artifact's version before this migration (null if it was created). */
    @Column(name = "previous_target_version", length = 32)
    private String previousTargetVersion;

    public MigrationOrder(Long sourceEnvironmentId, Long targetEnvironmentId, ArtifactType artifactType,
            Long artifactId, String artifactCode, MigrationStatus status) {
        this.sourceEnvironmentId = sourceEnvironmentId;
        this.targetEnvironmentId = targetEnvironmentId;
        this.artifactType = artifactType;
        this.artifactId = artifactId;
        this.artifactCode = artifactCode;
        this.status = status;
    }

    public void transitionTo(MigrationStatus target) {
        status.requireTransitionTo(target);
        this.status = target;
    }

    public void markMigrated(Long targetArtifactId, String targetVersion, String previousTargetVersion) {
        this.targetArtifactId = targetArtifactId;
        this.targetVersion = targetVersion;
        this.previousTargetVersion = previousTargetVersion;
    }
}
