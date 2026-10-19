package cn.org.openbanking.dcc.application.common;

import java.util.Optional;

import cn.org.openbanking.dcc.core.interfaceapi.repository.InterfaceDefinitionRepository;
import cn.org.openbanking.dcc.core.migration.MigrationOrder;
import cn.org.openbanking.dcc.core.migration.repository.MigrationOrderRepository;
import cn.org.openbanking.dcc.core.standard.repository.DataStandardRepository;
import cn.org.openbanking.dcc.core.table.repository.TableStructureRepository;
import cn.org.openbanking.dcc.core.template.repository.InterfaceTemplateRepository;
import cn.org.openbanking.dcc.core.tenant.TenantApplication;
import cn.org.openbanking.dcc.core.tenant.TenantEnvironment;
import cn.org.openbanking.dcc.core.tenant.repository.TenantApplicationRepository;
import cn.org.openbanking.dcc.core.tenant.repository.TenantEnvironmentRepository;

import org.springframework.stereotype.Component;

/**
 * Resolves the operating surface of a resource <em>before</em> authorization (ADR 0005).
 *
 * <p>Function-level {@code @PreAuthorize} sees only the URL, and an artifact's environment is a
 * property of the row rather than of the path — which is exactly why ADR 0004's scoped grants were
 * inert on opaque-id endpoints. This resolver closes that gap by reading the surface from the
 * database for the caller's tenant, so a scoped grant can be evaluated against the real target.
 *
 * <p>Returns empty when the row does not exist <em>or</em> belongs to another tenant: the caller
 * then falls back to its tenant-wide authority, and a foreign id can never activate a scoped grant.
 */
@Component
public class ResourceScopeResolver {

    private final DataStandardRepository standards;
    private final TableStructureRepository tables;
    private final InterfaceDefinitionRepository interfaces;
    private final InterfaceTemplateRepository templates;
    private final MigrationOrderRepository migrations;
    private final TenantEnvironmentRepository environments;
    private final TenantApplicationRepository applications;

    public ResourceScopeResolver(DataStandardRepository standards,
            TableStructureRepository tables,
            InterfaceDefinitionRepository interfaces,
            InterfaceTemplateRepository templates,
            MigrationOrderRepository migrations,
            TenantEnvironmentRepository environments,
            TenantApplicationRepository applications) {
        this.standards = standards;
        this.tables = tables;
        this.interfaces = interfaces;
        this.templates = templates;
        this.migrations = migrations;
        this.environments = environments;
        this.applications = applications;
    }

    /** @return the surface of one resource inside the tenant, or empty when unknown */
    public Optional<OperatingSurface> resolve(String tenantId, ArtifactResourceKind kind, Long id) {
        if (tenantId == null || kind == null || id == null) {
            return Optional.empty();
        }
        return switch (kind) {
            case DATA_STANDARD -> standards.findByTenantIdAndId(tenantId, id)
                    .map(standard -> surface(tenantId, standard.getEnvironmentId(), standard.getApplicationId()));
            case TABLE_STRUCTURE -> tables.findByTenantIdAndId(tenantId, id)
                    .map(table -> surface(tenantId, table.getEnvironmentId(), table.getApplicationId()));
            case INTERFACE -> interfaces.findByTenantIdAndId(tenantId, id)
                    .map(definition -> surface(tenantId, definition.getEnvironmentId(),
                            definition.getApplicationId()));
            case INTERFACE_TEMPLATE -> templates.findByTenantIdAndId(tenantId, id)
                    .map(template -> surface(tenantId, template.getEnvironmentId(), template.getApplicationId()));
            // a migration writes into its *target* environment; the artifact's own environment is the
            // source one, which must not authorize the approval
            case MIGRATION_ORDER -> migrations.findByTenantIdAndId(tenantId, id)
                    .map(order -> surface(tenantId, order.getTargetEnvironmentId(),
                            artifactApplicationId(tenantId, order)));
        };
    }

    /**
     * @return the application the migrated artifact belongs to, so an application-scoped grant can
     *         authorize the approval; {@code null} when the artifact is gone (environment-scoped
     *         grants still apply)
     */
    private Long artifactApplicationId(String tenantId, MigrationOrder order) {
        if (order.getArtifactId() == null) {
            return null;
        }
        return switch (order.getArtifactType()) {
            case DATA_STANDARD -> standards.findByTenantIdAndId(tenantId, order.getArtifactId())
                    .map(standard -> standard.getApplicationId()).orElse(null);
            case TABLE_STRUCTURE -> tables.findByTenantIdAndId(tenantId, order.getArtifactId())
                    .map(table -> table.getApplicationId()).orElse(null);
            case INTERFACE -> interfaces.findByTenantIdAndId(tenantId, order.getArtifactId())
                    .map(definition -> definition.getApplicationId()).orElse(null);
            case INTERFACE_TEMPLATE -> templates.findByTenantIdAndId(tenantId, order.getArtifactId())
                    .map(template -> template.getApplicationId()).orElse(null);
        };
    }

    private OperatingSurface surface(String tenantId, Long environmentId, Long applicationId) {
        String environmentCode = environmentId == null ? null
                : environments.findByTenantIdAndId(tenantId, environmentId)
                        .map(TenantEnvironment::getCode).orElse(null);
        String applicationCode = applicationId == null ? null
                : applications.findByTenantIdAndId(tenantId, applicationId)
                        .map(TenantApplication::getCode).orElse(null);
        return new OperatingSurface(environmentId, environmentCode, applicationId, applicationCode);
    }
}
