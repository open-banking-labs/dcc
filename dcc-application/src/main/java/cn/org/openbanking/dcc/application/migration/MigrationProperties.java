package cn.org.openbanking.dcc.application.migration;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for environment migration, bound from {@code dcc.migration.*}.
 *
 * <pre>
 * dcc:
 *   migration:
 *     target-environments: [PROD]
 * </pre>
 *
 * Environments whose code is listed here are "migration-target-only": their
 * artifacts cannot be maintained directly and must come from a migration.
 */
@ConfigurationProperties(prefix = "dcc.migration")
public class MigrationProperties {

    private List<String> targetEnvironments = new ArrayList<>(List.of("PROD"));

    public List<String> getTargetEnvironments() {
        return targetEnvironments;
    }

    public void setTargetEnvironments(List<String> targetEnvironments) {
        this.targetEnvironments = targetEnvironments;
    }
}
