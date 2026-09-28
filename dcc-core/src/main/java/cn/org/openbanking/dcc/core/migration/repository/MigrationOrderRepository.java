package cn.org.openbanking.dcc.core.migration.repository;

import java.util.List;
import java.util.Optional;

import cn.org.openbanking.dcc.core.migration.MigrationOrder;
import cn.org.openbanking.dcc.core.migration.MigrationStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence for {@link MigrationOrder}; every query is scoped by tenant. */
public interface MigrationOrderRepository extends JpaRepository<MigrationOrder, Long> {

    Optional<MigrationOrder> findByTenantIdAndId(String tenantId, Long id);

    @Query("select m from MigrationOrder m "
            + "where m.tenantId = :tenantId "
            + "and (:targetEnvironmentId is null or m.targetEnvironmentId = :targetEnvironmentId) "
            + "and (:status is null or m.status = :status) "
            + "order by m.id desc")
    List<MigrationOrder> search(@Param("tenantId") String tenantId,
            @Param("targetEnvironmentId") Long targetEnvironmentId,
            @Param("status") MigrationStatus status);
}
