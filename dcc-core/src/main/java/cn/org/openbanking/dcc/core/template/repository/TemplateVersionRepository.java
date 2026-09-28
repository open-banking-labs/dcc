package cn.org.openbanking.dcc.core.template.repository;

import java.util.List;
import java.util.Optional;

import cn.org.openbanking.dcc.core.template.TemplateVersion;

import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for {@link TemplateVersion} snapshots. */
public interface TemplateVersionRepository extends JpaRepository<TemplateVersion, Long> {

    List<TemplateVersion> findByTenantIdAndInterfaceTemplateIdOrderByIdAsc(String tenantId, Long interfaceTemplateId);

    Optional<TemplateVersion> findByTenantIdAndInterfaceTemplateIdAndVersion(String tenantId, Long interfaceTemplateId,
            String version);
}
