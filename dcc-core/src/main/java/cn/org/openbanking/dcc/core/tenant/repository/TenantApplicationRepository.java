package cn.org.openbanking.dcc.core.tenant.repository;

import java.util.List;
import java.util.Optional;

import cn.org.openbanking.dcc.core.tenant.TenantApplication;

import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for {@link TenantApplication}. Every query is scoped by tenant. */
public interface TenantApplicationRepository extends JpaRepository<TenantApplication, Long> {

    List<TenantApplication> findByTenantIdOrderByNameAsc(String tenantId);

    Optional<TenantApplication> findByTenantIdAndId(String tenantId, Long id);

    Optional<TenantApplication> findByTenantIdAndCode(String tenantId, String code);

    boolean existsByTenantIdAndCode(String tenantId, String code);
}
