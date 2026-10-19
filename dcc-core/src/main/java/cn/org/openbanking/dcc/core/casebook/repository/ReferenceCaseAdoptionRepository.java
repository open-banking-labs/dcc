package cn.org.openbanking.dcc.core.casebook.repository;

import java.util.List;

import cn.org.openbanking.dcc.core.casebook.ReferenceCaseAdoption;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Access to the adoption index. Rows are platform-level with an explicit {@code tenant_id} (see
 * {@link ReferenceCaseAdoption}), so each query here states its own tenant predicate: the tenant-side
 * method filters by tenant, the platform-side method deliberately does not.
 */
public interface ReferenceCaseAdoptionRepository extends JpaRepository<ReferenceCaseAdoption, Long> {

    /** @return one tenant's adoptions, newest first. */
    List<ReferenceCaseAdoption> findByTenantIdOrderByIdDesc(String tenantId);

    /** @return every tenant's adoption of one case, newest first (platform reach view). */
    List<ReferenceCaseAdoption> findByCaseIdOrderByIdDesc(Long caseId);

    /** @return any tenant's adoption of one case, newest first. */
    List<ReferenceCaseAdoption> findAllByOrderByIdDesc();
}
