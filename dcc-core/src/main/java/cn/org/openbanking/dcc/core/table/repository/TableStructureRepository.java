package cn.org.openbanking.dcc.core.table.repository;

import java.util.List;
import java.util.Optional;

import cn.org.openbanking.dcc.core.standard.StandardStatus;
import cn.org.openbanking.dcc.core.table.TableStructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence for {@link TableStructure}; every query is scoped by tenant. */
public interface TableStructureRepository extends JpaRepository<TableStructure, Long> {

    Optional<TableStructure> findByTenantIdAndId(String tenantId, Long id);

    Optional<TableStructure> findByTenantIdAndEnvironmentIdAndApplicationIdAndCode(
            String tenantId, Long environmentId, Long applicationId, String code);

    boolean existsByTenantIdAndEnvironmentIdAndApplicationIdAndCode(
            String tenantId, Long environmentId, Long applicationId, String code);

    @Query("select t from TableStructure t "
            + "where t.tenantId = :tenantId "
            + "and (:environmentId is null or t.environmentId = :environmentId) "
            + "and (:applicationId is null or t.applicationId = :applicationId) "
            + "and (:status is null or t.status = :status) "
            + "order by t.code")
    List<TableStructure> search(@Param("tenantId") String tenantId,
            @Param("environmentId") Long environmentId,
            @Param("applicationId") Long applicationId,
            @Param("status") StandardStatus status);
}
