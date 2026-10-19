package cn.org.openbanking.dcc.application.common;

import cn.org.openbanking.dcc.core.common.error.ValidationException;
import cn.org.openbanking.dcc.core.tenant.repository.TenantApplicationRepository;
import cn.org.openbanking.dcc.core.tenant.repository.TenantEnvironmentRepository;

import org.springframework.stereotype.Component;

/**
 * Validates the operating surface a write targets (ADR 0005).
 *
 * <p>The environment and application identifiers of an artifact come from the request
 * ({@code ?environmentId=}), and nothing on the entity ties them to the tenant: a caller could
 * otherwise write, say, a data standard scoped to <em>another tenant's</em> environment. Reads are
 * already protected by the tenant filter plus explicit predicates, so this closes the write side.
 *
 * <p>Reports "unknown" rather than "forbidden": a foreign id must not be distinguishable from a
 * missing one, or the endpoint becomes an existence oracle.
 */
@Component
public class ScopeValidation {

    private final TenantEnvironmentRepository environments;
    private final TenantApplicationRepository applications;

    public ScopeValidation(TenantEnvironmentRepository environments, TenantApplicationRepository applications) {
        this.environments = environments;
        this.applications = applications;
    }

    /** Throws unless the environment exists inside the given tenant. */
    public void requireEnvironment(String tenantId, Long environmentId) {
        if (environmentId == null
                || environments.findByTenantIdAndId(tenantId, environmentId).isEmpty()) {
            throw new ValidationException("environment " + environmentId + " is unknown");
        }
    }

    /** Throws unless the application exists inside the given tenant. */
    public void requireApplication(String tenantId, Long applicationId) {
        if (applicationId == null
                || applications.findByTenantIdAndId(tenantId, applicationId).isEmpty()) {
            throw new ValidationException("application " + applicationId + " is unknown");
        }
    }

    /** Convenience for the write paths that carry both identifiers. */
    public void requireSurface(String tenantId, Long environmentId, Long applicationId) {
        requireEnvironment(tenantId, environmentId);
        requireApplication(tenantId, applicationId);
    }
}
