package cn.org.openbanking.dcc.core.reference;

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
 * A recorded reference from an artifact (table structure / interface / template) to
 * a data standard. Maintained whenever an artifact version is saved, so "what
 * references this standard?" is an indexed lookup rather than a full scan - and so a
 * standard that is still referenced can never be deleted.
 */
@Entity
@Table(name = "standard_reference",
        uniqueConstraints = @UniqueConstraint(name = "uk_standard_reference",
                columnNames = {"tenant_id", "ref_type", "ref_object_id", "standard_id"}),
        indexes = @Index(name = "ix_standard_reference_standard", columnList = "tenant_id,standard_id"))
@Filter(name = TenantFilter.NAME, condition = "tenant_id = :tenantId")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StandardReference extends TenantScopedEntity {

    @Column(name = "standard_id", nullable = false, updatable = false)
    private Long standardId;

    @Enumerated(EnumType.STRING)
    @Column(name = "ref_type", nullable = false, updatable = false, length = 32)
    private ReferenceType refType;

    @Column(name = "ref_object_id", nullable = false, updatable = false)
    private Long refObjectId;

    @Column(name = "ref_object_code", length = 128)
    private String refObjectCode;

    public StandardReference(Long standardId, ReferenceType refType, Long refObjectId, String refObjectCode) {
        this.standardId = standardId;
        this.refType = refType;
        this.refObjectId = refObjectId;
        this.refObjectCode = refObjectCode;
    }
}
