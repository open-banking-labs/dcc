package cn.org.openbanking.dcc.core.casebook.repository;

import java.util.List;
import java.util.Optional;

import cn.org.openbanking.dcc.core.casebook.ReferenceCase;
import cn.org.openbanking.dcc.core.casebook.ReferenceCaseFeature;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Access to the platform-level reference-case library. Rows carry no {@code tenant_id}, so no
 * tenant filter applies; {@code @SQLRestriction} on {@code BaseEntity} keeps logically deleted
 * cases out of the default view.
 */
public interface ReferenceCaseRepository extends JpaRepository<ReferenceCase, Long> {

    /** @return the live cases of one feature, oldest first. */
    List<ReferenceCase> findByFeatureOrderByIdAsc(ReferenceCaseFeature feature);

    /** @return the live cases of one feature that are offered for adoption. */
    List<ReferenceCase> findByFeatureAndEnabledTrueOrderByIdAsc(ReferenceCaseFeature feature);

    /** @return all live cases, newest first (console list). */
    List<ReferenceCase> findAllByOrderByIdDesc();

    /** @return a live case by feature + code (the adoption address). */
    Optional<ReferenceCase> findByFeatureAndCode(ReferenceCaseFeature feature, String code);

    /** @return a case by id including logically deleted rows, for the retention restore. */
    @Query(value = "select * from reference_case where id = :id", nativeQuery = true)
    Optional<ReferenceCase> findByIdIncludingDeleted(@Param("id") Long id);
}
