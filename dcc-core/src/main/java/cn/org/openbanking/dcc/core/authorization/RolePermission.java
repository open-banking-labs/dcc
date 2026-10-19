package cn.org.openbanking.dcc.core.authorization;

import cn.org.openbanking.dcc.core.common.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One database-backed role → permission mapping (ADR 0005 §D8), used when
 * {@code dcc.security.resolver-mode=database}.
 *
 * <p>Platform-level configuration row: no {@code tenant_id}. Extending {@link BaseEntity} means a
 * mapping change is audited and a removed mapping is retained (logical delete), which matters when
 * "who could approve back in March" becomes an audit question.
 */
@Entity
@Table(name = "role_permission",
        indexes = @Index(name = "ix_role_permission_role", columnList = "role"))
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RolePermission extends BaseEntity {

    /** Role name as it appears in a token claim or a directory assignment. */
    @Column(name = "role", nullable = false, length = 64)
    private String role;

    /** Permission point name as declared by {@code DccPermissions} (e.g. {@code artifact.write}). */
    @Column(name = "permission", nullable = false, length = 64)
    private String permission;

    public RolePermission(String role, String permission) {
        this.role = role;
        this.permission = permission;
    }
}
