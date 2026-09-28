package cn.org.openbanking.dcc.core.tenant;

import cn.org.openbanking.dcc.core.common.entity.TenantScopedEntity;
import cn.org.openbanking.dcc.core.common.tenant.TenantFilter;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import org.hibernate.annotations.Filter;

/**
 * A custom environment inside a tenant (e.g. {@code DEV}, {@code TEST},
 * {@code PROD}). Standards, table structures, interfaces and templates are all
 * scoped to a tenant + environment (+ application).
 */
@Entity
@Table(name = "tenant_environment",
        uniqueConstraints = @UniqueConstraint(name = "uk_env_tenant_code", columnNames = {"tenant_id", "code"}))
@Filter(name = TenantFilter.NAME, condition = "tenant_id = :tenantId")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TenantEnvironment extends TenantScopedEntity {

    @Column(name = "code", nullable = false, length = 32)
    private String code;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "description", length = 512)
    private String description;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    public TenantEnvironment(String code, String name, String description, int sortOrder) {
        this.code = code;
        this.name = name;
        this.description = description;
        this.sortOrder = sortOrder;
    }
}
