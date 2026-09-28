package cn.org.openbanking.dcc.core.template.repository;

import java.util.List;
import java.util.Optional;

import cn.org.openbanking.dcc.core.standard.StandardStatus;
import cn.org.openbanking.dcc.core.template.InterfaceTemplate;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence for {@link InterfaceTemplate}; every query is scoped by tenant. */
public interface InterfaceTemplateRepository extends JpaRepository<InterfaceTemplate, Long> {

    Optional<InterfaceTemplate> findByTenantIdAndId(String tenantId, Long id);

    Optional<InterfaceTemplate> findByTenantIdAndEnvironmentIdAndApplicationIdAndCode(
            String tenantId, Long environmentId, Long applicationId, String code);

    boolean existsByTenantIdAndEnvironmentIdAndApplicationIdAndCode(
            String tenantId, Long environmentId, Long applicationId, String code);

    @Query("select t from InterfaceTemplate t "
            + "where t.tenantId = :tenantId "
            + "and (:environmentId is null or t.environmentId = :environmentId) "
            + "and (:applicationId is null or t.applicationId = :applicationId) "
            + "and (:status is null or t.status = :status) "
            + "order by t.code")
    List<InterfaceTemplate> search(@Param("tenantId") String tenantId,
            @Param("environmentId") Long environmentId,
            @Param("applicationId") Long applicationId,
            @Param("status") StandardStatus status);
}
