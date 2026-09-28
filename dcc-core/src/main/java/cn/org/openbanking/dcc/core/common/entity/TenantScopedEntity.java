package cn.org.openbanking.dcc.core.common.entity;

import cn.org.openbanking.dcc.core.common.tenant.CurrentTenant;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;

/**
 * Base for every entity that is isolated per tenant. It carries the
 * {@code tenant_id} row-level discriminator.
 *
 * <p>Two layers enforce isolation:
 * <ol>
 *   <li>the Hibernate {@code tenantFilter} (declared in
 *       {@code cn.org.openbanking.dcc.core.common.tenant.package-info}) scoped
 *       to {@link CurrentTenant}; and</li>
 *   <li>explicit {@code tenantId} predicates in every repository query.</li>
 * </ol>
 */
@MappedSuperclass
public abstract class TenantScopedEntity extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false, length = 64)
    private String tenantId;

    /**
     * Fills {@code tenantId} from {@link CurrentTenant} when a caller forgot to
     * set it explicitly, so data can never be persisted without a tenant.
     */
    @PrePersist
    void applyCurrentTenant() {
        if (tenantId == null) {
            tenantId = CurrentTenant.get();
        }
        if (tenantId == null) {
            throw new IllegalStateException(
                    "tenantId is not set and no CurrentTenant is bound for " + getClass().getSimpleName());
        }
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }
}
