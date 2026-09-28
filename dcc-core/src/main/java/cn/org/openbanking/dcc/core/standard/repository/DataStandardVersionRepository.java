package cn.org.openbanking.dcc.core.standard.repository;

import java.util.List;
import java.util.Optional;

import cn.org.openbanking.dcc.core.standard.DataStandardVersion;

import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for {@link DataStandardVersion} snapshots; queried within a tenant. */
public interface DataStandardVersionRepository extends JpaRepository<DataStandardVersion, Long> {

    List<DataStandardVersion> findByTenantIdAndStandardIdOrderByIdAsc(String tenantId, Long standardId);

    Optional<DataStandardVersion> findByTenantIdAndStandardIdAndVersion(String tenantId, Long standardId, String version);

    Optional<DataStandardVersion> findTopByTenantIdAndStandardIdOrderByIdDesc(String tenantId, Long standardId);
}
