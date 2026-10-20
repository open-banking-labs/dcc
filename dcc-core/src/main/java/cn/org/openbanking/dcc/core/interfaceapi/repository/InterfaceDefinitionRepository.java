package cn.org.openbanking.dcc.core.interfaceapi.repository;

import java.util.List;
import java.util.Optional;

import cn.org.openbanking.dcc.core.interfaceapi.InterfaceDefinition;
import cn.org.openbanking.dcc.core.standard.StandardStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence for {@link InterfaceDefinition}; every query is scoped by tenant. */
public interface InterfaceDefinitionRepository extends JpaRepository<InterfaceDefinition, Long> {

    Optional<InterfaceDefinition> findByTenantIdAndId(String tenantId, Long id);

    /** Loads a row including logically-deleted ones so it can be restored (native query). */
    @Query(value = "select * from interface_definition where tenant_id = :tenantId and id = :id", nativeQuery = true)
    Optional<InterfaceDefinition> findByTenantIdAndIdIncludingDeleted(@Param("tenantId") String tenantId,
            @Param("id") Long id);

    /** Interface numbers are unique within a tenant + environment. */
    Optional<InterfaceDefinition> findByTenantIdAndEnvironmentIdAndInterfaceNo(String tenantId, Long environmentId,
            String interfaceNo);

    boolean existsByTenantIdAndEnvironmentIdAndInterfaceNo(String tenantId, Long environmentId, String interfaceNo);

    @Query("select i from InterfaceDefinition i "
            + "where i.tenantId = :tenantId "
            + "and (:environmentId is null or i.environmentId = :environmentId) "
            + "and (:applicationId is null or i.applicationId = :applicationId) "
            + "and (:status is null or i.status = :status) "
            + "order by i.interfaceNo")
    List<InterfaceDefinition> search(@Param("tenantId") String tenantId,
            @Param("environmentId") Long environmentId,
            @Param("applicationId") Long applicationId,
            @Param("status") StandardStatus status);

    /**
     * Counts rows in one scope for the workbench dashboard. {@code environmentId},
     * {@code applicationId} and {@code status} may be {@code null} to mean "no constraint".
     */
    @Query("select count(i) from InterfaceDefinition i "
            + "where i.tenantId = :tenantId "
            + "and (:environmentId is null or i.environmentId = :environmentId) "
            + "and (:applicationId is null or i.applicationId = :applicationId) "
            + "and (:status is null or i.status = :status)")
    long countInScope(@Param("tenantId") String tenantId,
            @Param("environmentId") Long environmentId,
            @Param("applicationId") Long applicationId,
            @Param("status") StandardStatus status);
}
