package cn.org.openbanking.dcc.application.common;

import cn.org.openbanking.dcc.application.tenant.TenantProperties;
import cn.org.openbanking.dcc.core.common.error.TenantInactiveException;
import cn.org.openbanking.dcc.core.tenant.repository.TenantRepository;

import org.springframework.stereotype.Component;

/**
 * Revalidates the caller's tenant against the registry at use-case time. The token's
 * tenant claim is trusted at the security edge, but a tenant may be disabled or
 * logically deleted after the token was issued; this guard closes that window on
 * every {@link TenantScope} boundary.
 *
 * <p>The check is a single derived query on a small table ({@code tenant} carries
 * only platform rows). Logically-deleted rows are already excluded by the entity's
 * {@code @SQLRestriction}, so {@code existsByCodeAndEnabledTrue} covers both states.
 * {@code null} tenant ids pass through unchanged — they mark platform-level work
 * (seed data, system jobs) that is not scoped to a tenant.
 */
@Component
public class TenantStatusGuard {

    private final TenantRepository tenants;
    private final boolean enabled;

    public TenantStatusGuard(TenantRepository tenants, TenantProperties properties) {
        this.tenants = tenants;
        this.enabled = properties.isEnforceTenantStatus();
    }

    /**
     * Fails with {@link TenantInactiveException} unless the tenant is live and enabled.
     * A {@code null} tenant id skips the check (platform-level operation).
     */
    public void requireActive(String tenantId) {
        if (tenantId == null || !enabled) {
            return;
        }
        if (!tenants.existsByCodeAndEnabledTrue(tenantId)) {
            throw new TenantInactiveException(tenantId);
        }
    }
}
