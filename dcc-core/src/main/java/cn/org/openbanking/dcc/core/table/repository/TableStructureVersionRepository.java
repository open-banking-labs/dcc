package cn.org.openbanking.dcc.core.table.repository;

import java.util.List;
import java.util.Optional;

import cn.org.openbanking.dcc.core.table.TableStructureVersion;

import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for {@link TableStructureVersion} snapshots. */
public interface TableStructureVersionRepository extends JpaRepository<TableStructureVersion, Long> {

    List<TableStructureVersion> findByTenantIdAndTableStructureIdOrderByIdAsc(String tenantId, Long tableStructureId);

    Optional<TableStructureVersion> findByTenantIdAndTableStructureIdAndVersion(String tenantId, Long tableStructureId,
            String version);

    Optional<TableStructureVersion> findTopByTenantIdAndTableStructureIdOrderByIdDesc(String tenantId,
            Long tableStructureId);
}
