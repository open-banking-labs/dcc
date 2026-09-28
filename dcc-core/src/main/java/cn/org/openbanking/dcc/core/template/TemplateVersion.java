package cn.org.openbanking.dcc.core.template;

import cn.org.openbanking.dcc.core.common.entity.TenantScopedEntity;
import cn.org.openbanking.dcc.core.common.tenant.TenantFilter;
import cn.org.openbanking.dcc.core.standard.version.VersionChangeType;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Immutable, full-content JSON snapshot of an interface template at one version. */
@Entity
@Table(name = "template_version",
        uniqueConstraints = @UniqueConstraint(name = "uk_template_version",
                columnNames = {"interface_template_id", "version"}),
        indexes = @Index(name = "ix_template_version_template", columnList = "tenant_id,interface_template_id,id"))
@Filter(name = TenantFilter.NAME, condition = "tenant_id = :tenantId")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TemplateVersion extends TenantScopedEntity {

    @Column(name = "interface_template_id", nullable = false, updatable = false)
    private Long interfaceTemplateId;

    @Column(name = "version", nullable = false, length = 32, updatable = false)
    private String version;

    @Enumerated(EnumType.STRING)
    @Column(name = "change_type", nullable = false, length = 16)
    private VersionChangeType changeType;

    @Column(name = "change_summary", length = 1024)
    private String changeSummary;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "content_json", nullable = false)
    private String contentJson;

    @Column(name = "source_environment_id")
    private Long sourceEnvironmentId;

    @Column(name = "source_version", length = 32)
    private String sourceVersion;

    // ---- three-axis provenance ----
    @Column(name = "content_hash", length = 71)
    private String contentHash;

    @Column(name = "parent_hash", length = 71)
    private String parentHash;

    @Column(name = "author", length = 128)
    private String author;

    @Column(name = "message", length = 1024)
    private String message;

    public TemplateVersion(Long interfaceTemplateId, String version, VersionChangeType changeType,
            String contentJson, String changeSummary) {
        this.interfaceTemplateId = interfaceTemplateId;
        this.version = version;
        this.changeType = changeType;
        this.contentJson = contentJson;
        this.changeSummary = changeSummary;
    }

    public void markSource(Long sourceEnvironmentId, String sourceVersion) {
        this.sourceEnvironmentId = sourceEnvironmentId;
        this.sourceVersion = sourceVersion;
    }

    /** Records the three-axis provenance: content identity + Git-like lineage. */
    public void stamp(String contentHash, String parentHash, String author, String message) {
        this.contentHash = contentHash;
        this.parentHash = parentHash;
        this.author = author;
        this.message = message;
    }
}
