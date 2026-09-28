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
 * An application inside a tenant (e.g. "公共基础", "账务系统", "客户系统"). Standards,
 * interfaces and templates are owned by a tenant + environment + application.
 */
@Entity
@Table(name = "tenant_application",
        uniqueConstraints = @UniqueConstraint(name = "uk_app_tenant_code", columnNames = {"tenant_id", "code"}))
@Filter(name = TenantFilter.NAME, condition = "tenant_id = :tenantId")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TenantApplication extends TenantScopedEntity {

    @Column(name = "code", nullable = false, length = 64)
    private String code;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "description", length = 512)
    private String description;

    public TenantApplication(String code, String name, String description) {
        this.code = code;
        this.name = name;
        this.description = description;
    }
}
