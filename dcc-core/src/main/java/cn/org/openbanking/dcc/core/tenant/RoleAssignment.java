package cn.org.openbanking.dcc.core.tenant;

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

/**
 * Directory record: one role a subject is granted inside one tenant. Stored role names
 * are free-form and go through the same {@code dcc.security.role-authorities} mapping as
 * token roles, so deployments can assign IdP naming schemes or logical authority names.
 *
 * <p>Assignments are **additive** to the token's claim roles: the directory can grant,
 * it never overrides what the identity provider already put on the token (restricting
 * IdP roles is the IdP's job, or {@code dcc.security.roles-passthrough: false}). Managed
 * platform-level like {@link TenantMembership}; live-uniqueness of
 * {@code (tenant, subject, role, scope_type, scope_code)} is a partial unique index in
 * the schema. The optional scope narrows the grant to one operating surface (ADR 0004);
 * requests select theirs via the {@code X-DCC-Scope} header.
 */
@Entity
@Table(name = "role_assignment")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoleAssignment extends BaseEntity {

    /** Authenticated subject id (the JWT {@code sub}). */
    @Column(name = "subject", nullable = false, length = 128, updatable = false)
    private String subject;

    /** Business tenant code the role is granted in. */
    @Column(name = "tenant_code", nullable = false, length = 64, updatable = false)
    private String tenantCode;

    /** Role name as fed into {@code dcc.security.role-authorities}. */
    @Column(name = "role", nullable = false, length = 64)
    private String role;

    /** Operating surface the grant is valid for; {@link ScopeType#TENANT} = tenant-wide. */
    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type", nullable = false, length = 16, updatable = false)
    private ScopeType scopeType = ScopeType.TENANT;

    /** Business code of the scoped environment / application; null for tenant-wide. */
    @Column(name = "scope_code", length = 64, updatable = false)
    private String scopeCode;

    public RoleAssignment(String subject, String tenantCode, String role) {
        this(subject, tenantCode, role, ScopeType.TENANT, null);
    }

    public RoleAssignment(String subject, String tenantCode, String role, ScopeType scopeType, String scopeCode) {
        this.subject = subject;
        this.tenantCode = tenantCode;
        this.role = role;
        this.scopeType = scopeType;
        this.scopeCode = scopeCode;
    }
}
