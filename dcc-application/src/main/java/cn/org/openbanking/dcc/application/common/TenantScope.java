package cn.org.openbanking.dcc.application.common;

import java.util.function.Supplier;

import cn.org.openbanking.dcc.core.common.tenant.CurrentTenant;

import org.springframework.stereotype.Component;

/**
 * Binds the tenant of a use case to {@link CurrentTenant} for its duration, so
 * persistence and the Hibernate {@code tenantFilter} (see {@link TenantFilterAspect})
 * scope reads/writes without threading the tenant through every internal call.
 *
 * <p>The use-case layer receives the tenant explicitly (the exposure layer reads it
 * from the authenticated caller), which keeps the layer protocol-agnostic and the
 * services unit-testable without a request context.
 */
@Component
public class TenantScope {

    public <T> T call(String tenantId, Supplier<T> action) {
        String previous = CurrentTenant.get();
        CurrentTenant.set(tenantId);
        try {
            return action.get();
        } finally {
            if (previous == null) {
                CurrentTenant.clear();
            } else {
                CurrentTenant.set(previous);
            }
        }
    }

    public void run(String tenantId, Runnable action) {
        call(tenantId, () -> {
            action.run();
            return null;
        });
    }
}
