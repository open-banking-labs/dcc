package cn.org.openbanking.dcc.core.tenant.repository;

import java.util.List;
import java.util.Optional;

import cn.org.openbanking.dcc.core.tenant.TenantEnvironment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence for {@link TenantEnvironment}. Every query is scoped by tenant. */
public interface TenantEnvironmentRepository extends JpaRepository<TenantEnvironment, Long> {

    List<TenantEnvironment> findByTenantIdOrderBySortOrderAsc(String tenantId);

    Optional<TenantEnvironment> findByTenantIdAndId(String tenantId, Long id);

    /** Loads a row including logically-deleted ones so it can be restored (native query). */
    @Query(value = "select * from tenant_environment where tenant_id = :tenantId and id = :id", nativeQuery = true)
    Optional<TenantEnvironment> findByTenantIdAndIdIncludingDeleted(@Param("tenantId") String tenantId,
            @Param("id") Long id);

    Optional<TenantEnvironment> findByTenantIdAndCode(String tenantId, String code);

    boolean existsByTenantIdAndCode(String tenantId, String code);
}
