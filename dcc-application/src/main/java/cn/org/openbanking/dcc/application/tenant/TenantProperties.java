package cn.org.openbanking.dcc.application.tenant;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Tenant-model configuration, bound from {@code dcc.tenant.*}. The tenant of a
 * request is decided by {@code dcc-security} (from the token, see
 * {@code dcc.security.tenant-claim}); this records the platform defaults and the
 * isolation level the deployment targets.
 *
 * <pre>
 * dcc:
 *   tenant:
 *     default-tenant: default_tenant     # used where no caller tenant applies
 *     system-operator: system            # author recorded for system changes
 *     isolation: ROW                     # ROW | TABLE | SCHEMA
 * </pre>
 */
@ConfigurationProperties(prefix = "dcc.tenant")
public class TenantProperties {

    /** Tenant id used where no caller tenant applies (seed data, system jobs). */
    private String defaultTenant = "default_tenant";

    /** Author recorded for changes not made by an authenticated subject. */
    private String systemOperator = "system";

    /**
     * Targeted tenant isolation level. Only {@link Isolation#ROW} (a Hibernate
     * row-level filter on {@code tenant_id}) is implemented today; the others are
     * recorded so deployments can declare intent and future levels can branch on it.
     */
    private Isolation isolation = Isolation.ROW;

    /** Tenant isolation granularity. */
    public enum Isolation {
        /** Row-level: every table carries {@code tenant_id}, filtered per session. */
        ROW,
        /** Table-level: one set of tables per tenant. */
        TABLE,
        /** Schema-level: one database schema per tenant. */
        SCHEMA
    }

    public String getDefaultTenant() {
        return defaultTenant;
    }

    public void setDefaultTenant(String defaultTenant) {
        this.defaultTenant = defaultTenant;
    }

    public String getSystemOperator() {
        return systemOperator;
    }

    public void setSystemOperator(String systemOperator) {
        this.systemOperator = systemOperator;
    }

    public Isolation getIsolation() {
        return isolation;
    }

    public void setIsolation(Isolation isolation) {
        this.isolation = isolation;
    }
}
