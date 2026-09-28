package cn.org.openbanking.dcc.core.reference.repository;

import java.util.List;

import cn.org.openbanking.dcc.core.reference.ReferenceType;
import cn.org.openbanking.dcc.core.reference.StandardReference;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence for {@link StandardReference}; all lookups are tenant-scoped. */
public interface StandardReferenceRepository extends JpaRepository<StandardReference, Long> {

    List<StandardReference> findByTenantIdAndStandardId(String tenantId, Long standardId);

    List<StandardReference> findByTenantIdAndRefTypeAndRefObjectId(String tenantId, ReferenceType refType,
            Long refObjectId);

    /**
     * Immediate (bulk) delete of one artifact's references. Unlike a derived
     * {@code deleteBy...}, this runs its DELETE at call time rather than at commit,
     * so a delete-then-reinsert of the same reference within one transaction cannot
     * collide with a row still awaiting deletion.
     */
    @Modifying
    @Query("delete from StandardReference r where r.tenantId = :tenantId "
            + "and r.refType = :refType and r.refObjectId = :refObjectId")
    void deleteByScope(@Param("tenantId") String tenantId,
            @Param("refType") ReferenceType refType,
            @Param("refObjectId") Long refObjectId);
}
