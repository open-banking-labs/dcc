package cn.org.openbanking.dcc.core.casebook;

import cn.org.openbanking.dcc.core.common.entity.BaseEntity;

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

/**
 * Machine-readable provenance of one adoption (ADR 0005 §D7 follow-up).
 *
 * <p>The immutable version message records <em>that</em> an artifact came from a case; this row
 * records <em>which</em> case and version, so two questions stay answerable without reading
 * free text: "which of my objects came from an outdated case?" (tenant side) and "who adopted this
 * case?" (platform side).
 *
 * <p>Platform-level row with an explicit {@code tenant_id} (like {@code role_assignment}), so both
 * sides can query it; being outside the tenant filter is deliberate — a platform query must be able
 * to cross tenants, and every query adds its own tenant predicate when it should be tenant-scoped.
 */
@Entity
@Table(name = "reference_case_adoption",
        indexes = {
                @Index(name = "ix_case_adoption_tenant", columnList = "tenant_id,id"),
                @Index(name = "ix_case_adoption_case", columnList = "case_id,id")
        })
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReferenceCaseAdoption extends BaseEntity {

    /** Tenant the case was adopted into. */
    @Column(name = "tenant_id", nullable = false, updatable = false, length = 64)
    private String tenantId;

    /** The adopted case. */
    @Column(name = "case_id", nullable = false, updatable = false)
    private Long caseId;

    /** Case code at adoption time (stable identifier, kept for display after a rename). */
    @Column(name = "case_code", nullable = false, length = 128)
    private String caseCode;

    /** Case version at adoption time; compared with the case's current version to flag staleness. */
    @Column(name = "case_version", nullable = false, length = 32)
    private String caseVersion;

    /** What the adoption produced in the tenant. */
    @Enumerated(EnumType.STRING)
    @Column(name = "artifact_type", nullable = false, length = 32)
    private ReferenceCaseFeature artifactType;

    /** Id of the created artifact; {@code null} for a skeleton adoption (no single artifact). */
    @Column(name = "artifact_id")
    private Long artifactId;

    /** Business code of the created artifact, for the tenant-side list. */
    @Column(name = "artifact_code", length = 128)
    private String artifactCode;

    /** Subject that performed the adoption. */
    @Column(name = "adopted_by", length = 128)
    private String adoptedBy;

    public ReferenceCaseAdoption(String tenantId, ReferenceCase referenceCase, ReferenceCaseFeature artifactType,
            Long artifactId, String artifactCode, String adoptedBy) {
        this.tenantId = tenantId;
        this.caseId = referenceCase.getId();
        this.caseCode = referenceCase.getCode();
        this.caseVersion = referenceCase.getCaseVersion();
        this.artifactType = artifactType;
        this.artifactId = artifactId;
        this.artifactCode = artifactCode;
        this.adoptedBy = adoptedBy;
    }
}
