package cn.org.openbanking.dcc.core.authorization.repository;

import java.util.Collection;
import java.util.List;

import cn.org.openbanking.dcc.core.authorization.RolePermission;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Access to the database-backed role → permission mappings. {@code @SQLRestriction} on
 * {@code BaseEntity} keeps logically deleted mappings out, so a revoked mapping stops conferring
 * power immediately while its audited row is retained.
 */
public interface RolePermissionRepository extends JpaRepository<RolePermission, Long> {

    /** @return the live mappings of the given roles. */
    List<RolePermission> findByRoleIn(Collection<String> roles);
}
