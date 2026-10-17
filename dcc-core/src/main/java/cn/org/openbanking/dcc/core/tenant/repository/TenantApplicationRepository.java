package cn.org.openbanking.dcc.core.tenant.repository;

import java.util.List;
import java.util.Optional;

import cn.org.openbanking.dcc.core.tenant.TenantApplication;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence for {@link TenantApplication}. Every query is scoped by tenant. */
public interface TenantApplicationRepository extends JpaRepository<TenantApplication, Long> {

    List<TenantApplication> findByTenantIdOrderByNameAsc(String tenantId);

    Optional<TenantApplication> findByTenantIdAndId(String tenantId, Long id);

    /** Loads a row including logically-deleted ones so it can be restored (native query). */
    @Query(value = "select * from tenant_application where tenant_id = :tenantId and id = :id", nativeQuery = true)
    Optional<TenantApplication> findByTenantIdAndIdIncludingDeleted(@Param("tenantId") String tenantId,
            @Param("id") Long id);

    Optional<TenantApplication> findByTenantIdAndCode(String tenantId, String code);

    boolean existsByTenantIdAndCode(String tenantId, String code);
}
