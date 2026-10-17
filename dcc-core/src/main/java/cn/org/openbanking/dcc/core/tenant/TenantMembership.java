package cn.org.openbanking.dcc.core.tenant;

import cn.org.openbanking.dcc.core.common.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Directory record: a subject's permission to act inside one tenant. The JWT's tenant
 * claim stays the *selector* of the tenant (the console switches tenants by claiming
 * another one), but under {@code dcc.security.directory-mode} the claim is only honoured
 * when a live {@link TenantMembership} exists for the subject here.
 *
 * <p>Like {@link Tenant}, it is the isolation root, so it carries no {@code tenant_id}
 * and is managed platform-level (admin-only); the Hibernate {@code tenantFilter} does
 * not apply. Uniqueness of a live (tenant, subject) pair is a partial unique index in
 * the schema, so a historical row never blocks a re-grant.
 */
@Entity
@Table(name = "tenant_membership")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TenantMembership extends BaseEntity {

    /** Authenticated subject id (the JWT {@code sub}). */
    @Column(name = "subject", nullable = false, length = 128, updatable = false)
    private String subject;

    /** Business tenant code the subject may act in. */
    @Column(name = "tenant_code", nullable = false, length = 64, updatable = false)
    private String tenantCode;

    public TenantMembership(String subject, String tenantCode) {
        this.subject = subject;
        this.tenantCode = tenantCode;
    }
}
