package cn.org.openbanking.dcc.core.tenant;

import cn.org.openbanking.dcc.core.common.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A tenant of the SaaS deployment. Data, permissions and generated artifacts are
 * isolated per tenant; nothing is shared across tenants.
 *
 * <p>The tenant is the isolation root itself, so it is not {@code tenant}-scoped
 * (it carries no {@code tenant_id} filter); it is managed by platform-level
 * operations.
 */
@Entity
@Table(name = "tenant", uniqueConstraints = @UniqueConstraint(name = "uk_tenant_code", columnNames = "code"))
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Tenant extends BaseEntity {

    @Column(name = "code", nullable = false, length = 64, updatable = false)
    private String code;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "description", length = 512)
    private String description;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    public Tenant(String code, String name, String description) {
        this.code = code;
        this.name = name;
        this.description = description;
    }
}
