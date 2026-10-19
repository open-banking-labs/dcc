package cn.org.openbanking.dcc.core.casebook;

import cn.org.openbanking.dcc.core.common.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A platform-authored guidance example (指导性案例, ADR 0005 §D7).
 *
 * <p>Platform-level row (extends {@link BaseEntity}, not the tenant-scoped base): it belongs to no
 * tenant, is authored only by the system administrator, and is read/adopted by tenant
 * administrators. Adoption copies the payload into the tenant as a <em>draft</em> artifact and
 * records the case code + version in the immutable version message, so "copied or written from
 * scratch" stays answerable. Cases are never pushed into a tenant: a later edit of a case does not
 * change what a tenant already adopted.
 */
@Entity
@Table(name = "reference_case")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReferenceCase extends BaseEntity {

    /** Which feature this case is an example of. */
    @Enumerated(EnumType.STRING)
    @Column(name = "feature", nullable = false, length = 32)
    private ReferenceCaseFeature feature;

    /** Stable identifier of the case inside its feature (used in the adoption provenance). */
    @Column(name = "code", nullable = false, length = 128)
    private String code;

    /** Display name shown in the console's case library. */
    @Column(name = "name", nullable = false, length = 255)
    private String name;

    /** What the case demonstrates, and when to use it. */
    @Column(name = "description", length = 1024)
    private String description;

    /** Feature payload: artifact content JSON, or the skeleton for {@code SKELETON}. */
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "content", nullable = false)
    private String content;

    /** Version of the case itself, echoed into the adoption provenance. */
    @Column(name = "case_version", nullable = false, length = 32)
    private String caseVersion;

    /** Whether the case is offered for adoption; disabling hides it without deleting history. */
    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    public ReferenceCase(ReferenceCaseFeature feature, String code, String name, String description,
            String content, String caseVersion) {
        this.feature = feature;
        this.code = code;
        this.name = name;
        this.description = description;
        this.content = content;
        this.caseVersion = caseVersion;
    }
}
