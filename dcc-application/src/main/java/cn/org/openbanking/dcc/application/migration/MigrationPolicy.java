package cn.org.openbanking.dcc.application.migration;

import cn.org.openbanking.dcc.application.common.MigrationContext;
import cn.org.openbanking.dcc.core.common.error.ConflictException;
import cn.org.openbanking.dcc.core.tenant.repository.TenantEnvironmentRepository;

import org.springframework.stereotype.Component;

/**
 * Enforces "a target environment's version can only come from a migration": direct
 * create/update is rejected for environments configured as migration targets
 * ({@code dcc.migration.target-environments}, default {@code PROD}).
 *
 * <p>The guard is bypassed while a {@link MigrationContext} is active, so the
 * migration itself may write to the target environment.
 */
@Component
public class MigrationPolicy {

    private final MigrationProperties properties;
    private final TenantEnvironmentRepository environments;
    private final MigrationContext context;

    public MigrationPolicy(MigrationProperties properties, TenantEnvironmentRepository environments,
            MigrationContext context) {
        this.properties = properties;
        this.environments = environments;
        this.context = context;
    }

    /** Throws unless the environment accepts direct maintenance. */
    public void requireDirectMaintenanceAllowed(String tenantId, Long environmentId) {
        if (context.active()) {
            return;
        }
        if (isMigrationTarget(tenantId, environmentId)) {
            throw new ConflictException("environment is migration-target-only; changes must be migrated "
                    + "from a source environment");
        }
    }

    public boolean isMigrationTarget(String tenantId, Long environmentId) {
        return environments.findByTenantIdAndId(tenantId, environmentId)
                .map(environment -> properties.getTargetEnvironments().stream()
                        .anyMatch(code -> code.equalsIgnoreCase(environment.getCode())))
                .orElse(false);
    }
}
