package cn.org.openbanking.dcc.core.standard.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import cn.org.openbanking.dcc.core.standard.DataStandard;
import cn.org.openbanking.dcc.core.standard.StandardStatus;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    /**
     * Loads a row <em>including</em> logically-deleted ones so it can be restored. Native
     * SQL is used because the entity-level {@code @SQLRestriction} is only applied to
     * Hibernate (HQL/derived) queries, not to native ones.
     */
    @Query(value = "select * from data_standard where tenant_id = :tenantId and id = :id", nativeQuery = true)
    Optional<DataStandard> findByTenantIdAndIdIncludingDeleted(@Param("tenantId") String tenantId,
            @Param("id") Long id);

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

    /**
     * Paged filtered search for the console. {@code keyword} is a pre-escaped,
     * lower-cased {@code %pattern%} matched against the code or the current version's
     * name with {@code escape '!'}, so a literal {@code %} or {@code _} has no
     * wildcard meaning and {@code null} means "no keyword".
     *
     * <p>The name lives on the version snapshot, so it is matched with an
     * {@code exists} subquery rather than a join: a row still appears when only its
     * code matches, and the count query stays one row per standard.
     */
    @Query(value = "select s from DataStandard s "
            + "where s.tenantId = :tenantId "
            + "and (:environmentId is null or s.environmentId = :environmentId) "
            + "and (:applicationId is null or s.applicationId = :applicationId) "
            + "and (:category is null or s.category = :category) "
            + "and (:status is null or s.status = :status) "
            + "and (:keyword is null "
            + "     or lower(s.code) like :keyword escape '!' "
            + "     or exists (select v.id from DataStandardVersion v "
            + "                 where v.id = s.currentVersionId "
            + "                 and lower(v.name) like :keyword escape '!')) "
            + "order by s.code",
            countQuery = "select count(s) from DataStandard s "
                    + "where s.tenantId = :tenantId "
                    + "and (:environmentId is null or s.environmentId = :environmentId) "
                    + "and (:applicationId is null or s.applicationId = :applicationId) "
                    + "and (:category is null or s.category = :category) "
                    + "and (:status is null or s.status = :status) "
                    + "and (:keyword is null "
                    + "     or lower(s.code) like :keyword escape '!' "
                    + "     or exists (select v.id from DataStandardVersion v "
                    + "                 where v.id = s.currentVersionId "
                    + "                 and lower(v.name) like :keyword escape '!'))")
    Page<DataStandard> searchPage(@Param("tenantId") String tenantId,
            @Param("environmentId") Long environmentId,
            @Param("applicationId") Long applicationId,
            @Param("category") String category,
            @Param("status") StandardStatus status,
            @Param("keyword") String keyword,
            Pageable pageable);

    /** Loads a batch of rows by id inside one tenant (summary pages, export jobs). */
    List<DataStandard> findByTenantIdAndIdIn(String tenantId, Collection<Long> ids);

    /**
     * Counts rows in one scope for the workbench dashboard. {@code environmentId},
     * {@code applicationId} and {@code status} may be {@code null} to mean "no constraint".
     */
    @Query("select count(s) from DataStandard s "
            + "where s.tenantId = :tenantId "
            + "and (:environmentId is null or s.environmentId = :environmentId) "
            + "and (:applicationId is null or s.applicationId = :applicationId) "
            + "and (:status is null or s.status = :status)")
    long countInScope(@Param("tenantId") String tenantId,
            @Param("environmentId") Long environmentId,
            @Param("applicationId") Long applicationId,
            @Param("status") StandardStatus status);
}
