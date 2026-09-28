package cn.org.openbanking.dcc.core.standard.repository;

import java.util.List;
import java.util.Optional;

import cn.org.openbanking.dcc.core.standard.DataStandard;
import cn.org.openbanking.dcc.core.standard.StandardStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Persistence for {@link DataStandard}.
 *
 * <p>Every query carries an explicit {@code tenantId} predicate (row-level
 * isolation); the Hibernate {@code tenantFilter} is defence in depth on top.
 */
public interface DataStandardRepository extends JpaRepository<DataStandard, Long> {

    Optional<DataStandard> findByTenantIdAndId(String tenantId, Long id);

    Optional<DataStandard> findByTenantIdAndEnvironmentIdAndApplicationIdAndCode(
            String tenantId, Long environmentId, Long applicationId, String code);

    boolean existsByTenantIdAndEnvironmentIdAndApplicationIdAndCode(
            String tenantId, Long environmentId, Long applicationId, String code);

    /**
     * Flexible filtered search. Any of {@code environmentId}, {@code applicationId},
     * {@code category}, {@code status} may be {@code null} to mean "no constraint".
     */
    @Query("select s from DataStandard s "
            + "where s.tenantId = :tenantId "
            + "and (:environmentId is null or s.environmentId = :environmentId) "
            + "and (:applicationId is null or s.applicationId = :applicationId) "
            + "and (:category is null or s.category = :category) "
            + "and (:status is null or s.status = :status) "
            + "order by s.code")
    List<DataStandard> search(@Param("tenantId") String tenantId,
            @Param("environmentId") Long environmentId,
            @Param("applicationId") Long applicationId,
            @Param("category") String category,
            @Param("status") StandardStatus status);
}
