package cn.org.openbanking.dcc.core.tenant.repository;

import java.util.Optional;

import cn.org.openbanking.dcc.core.tenant.Tenant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence for {@link Tenant}. Tenants are the isolation root, so queries are global. */
public interface TenantRepository extends JpaRepository<Tenant, Long> {

    Optional<Tenant> findByCode(String code);

    boolean existsByCode(String code);

    /** Loads a tenant including logically-deleted ones so it can be restored (native query). */
    @Query(value = "select * from tenant where code = :code", nativeQuery = true)
    Optional<Tenant> findByCodeIncludingDeleted(@Param("code") String code);
}
