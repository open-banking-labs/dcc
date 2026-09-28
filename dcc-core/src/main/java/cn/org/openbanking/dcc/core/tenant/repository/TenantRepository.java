package cn.org.openbanking.dcc.core.tenant.repository;

import java.util.Optional;

import cn.org.openbanking.dcc.core.tenant.Tenant;

import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for {@link Tenant}. Tenants are the isolation root, so queries are global. */
public interface TenantRepository extends JpaRepository<Tenant, Long> {

    Optional<Tenant> findByCode(String code);

    boolean existsByCode(String code);
}
