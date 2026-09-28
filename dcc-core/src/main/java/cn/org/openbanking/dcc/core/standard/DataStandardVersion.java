package cn.org.openbanking.dcc.core.standard;

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

/**
 * An immutable, full-content snapshot of a data standard at one version. History,
 * diff and rollback all read from these rows, never by rewriting them.
 *
 * <p>{@code sourceEnvironmentId} / {@code sourceVersion} record provenance
 * (which environment/version a snapshot was migrated or rolled back from) so
 * lineage is traceable once environment migration lands.
 */
@Entity
@Table(name = "data_standard_version",
        uniqueConstraints = @UniqueConstraint(name = "uk_standard_version",
                columnNames = {"standard_id", "version"}),
        indexes = @Index(name = "ix_standard_version_standard", columnList = "tenant_id,standard_id,id"))
@Filter(name = TenantFilter.NAME, condition = "tenant_id = :tenantId")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DataStandardVersion extends TenantScopedEntity {

    @Column(name = "standard_id", nullable = false, updatable = false)
    private Long standardId;

    @Column(name = "version", nullable = false, length = 32, updatable = false)
    private String version;

    @Enumerated(EnumType.STRING)
    @Column(name = "change_type", nullable = false, length = 16)
    private VersionChangeType changeType;

    @Column(name = "change_summary", length = 1024)
    private String changeSummary;

    // ---- content snapshot ----
    @Column(name = "name", nullable = false, length = 256)
    private String name;

    @Column(name = "description", length = 1024)
    private String description;

    @Column(name = "data_type", nullable = false, length = 64)
    private String dataType;

    @Column(name = "length")
    private Integer length;

    @Column(name = "scale")
    private Integer scale;

    @Column(name = "required", nullable = false)
    private boolean required;

    @Column(name = "default_value", length = 256)
    private String defaultValue;

    @Column(name = "enum_values", length = 1024)
    private String enumValues;

    @Column(name = "regex", length = 512)
    private String regex;

    @Column(name = "example_value", length = 512)
    private String exampleValue;

    // ---- provenance ----
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

    public DataStandardVersion(Long standardId, String version, VersionChangeType changeType, StandardContent content,
            String changeSummary) {
        this.standardId = standardId;
        this.version = version;
        this.changeType = changeType;
        this.changeSummary = changeSummary;
        apply(content);
    }

    /** Overwrites the snapshot columns from a content record. */
    public void apply(StandardContent content) {
        this.name = content.name();
        this.description = content.description();
        this.dataType = content.dataType();
        this.length = content.length();
        this.scale = content.scale();
        this.required = content.required();
        this.defaultValue = content.defaultValue();
        this.enumValues = content.enumValues();
        this.regex = content.regex();
        this.exampleValue = content.exampleValue();
    }

    /** @return this snapshot as a content record for diffing. */
    public StandardContent content() {
        return new StandardContent(name, description, dataType, length, scale, required,
                defaultValue, enumValues, regex, exampleValue);
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
    }}
