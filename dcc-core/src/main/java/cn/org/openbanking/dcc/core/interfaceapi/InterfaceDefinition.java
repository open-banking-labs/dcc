package cn.org.openbanking.dcc.core.interfaceapi;

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
 * Master record of a business interface. {@code interfaceNo} is unique within a
 * tenant + environment (not per application); the versioned content lives in
 * {@link InterfaceVersion}. {@code templateId} binds the interface to an
 * application's interface template.
 */
@Entity
@Table(name = "interface_definition",
        uniqueConstraints = @UniqueConstraint(name = "uk_interface_tenant_env_no",
                columnNames = {"tenant_id", "environment_id", "interface_no"}),
        indexes = @Index(name = "ix_interface_scope", columnList = "tenant_id,environment_id,application_id"))
@Filter(name = TenantFilter.NAME, condition = "tenant_id = :tenantId")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InterfaceDefinition extends TenantScopedEntity {

    @Column(name = "environment_id", nullable = false, updatable = false)
    private Long environmentId;

    @Column(name = "application_id", nullable = false, updatable = false)
    private Long applicationId;

    @Column(name = "interface_no", nullable = false, length = 64, updatable = false)
    private String interfaceNo;

    @Column(name = "template_id")
    private Long templateId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private StandardStatus status;

    @Column(name = "current_version", nullable = false, length = 32)
    private String currentVersion;

    @Column(name = "current_version_id")
    private Long currentVersionId;

    public InterfaceDefinition(Long environmentId, Long applicationId, String interfaceNo, Long templateId,
            StandardStatus status, String currentVersion) {
        this.environmentId = environmentId;
        this.applicationId = applicationId;
        this.interfaceNo = interfaceNo;
        this.templateId = templateId;
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
