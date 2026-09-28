package cn.org.openbanking.dcc.core.tenant.repository;

import java.util.List;
import java.util.Optional;

import cn.org.openbanking.dcc.core.tenant.TenantEnvironment;

import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for {@link TenantEnvironment}. Every query is scoped by tenant. */
public interface TenantEnvironmentRepository extends JpaRepository<TenantEnvironment, Long> {

    List<TenantEnvironment> findByTenantIdOrderBySortOrderAsc(String tenantId);

    Optional<TenantEnvironment> findByTenantIdAndId(String tenantId, Long id);

    Optional<TenantEnvironment> findByTenantIdAndCode(String tenantId, String code);

    boolean existsByTenantIdAndCode(String tenantId, String code);
}
