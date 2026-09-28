package cn.org.openbanking.dcc.core.interfaceapi.repository;

import java.util.List;
import java.util.Optional;

import cn.org.openbanking.dcc.core.interfaceapi.InterfaceVersion;

import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for {@link InterfaceVersion} snapshots. */
public interface InterfaceVersionRepository extends JpaRepository<InterfaceVersion, Long> {

    List<InterfaceVersion> findByTenantIdAndInterfaceDefinitionIdOrderByIdAsc(String tenantId,
            Long interfaceDefinitionId);

    Optional<InterfaceVersion> findByTenantIdAndInterfaceDefinitionIdAndVersion(String tenantId,
            Long interfaceDefinitionId, String version);
}
