package cn.org.openbanking.dcc.core.common.tenant;

/**
 * Name of the Hibernate row-level filter that scopes tenant-owned entities.
 *
 * <p>The filter is declared once
 * (see {@code package-info.java} in this package) and attached to every
 * {@link cn.org.openbanking.dcc.core.common.entity.TenantScopedEntity}. It is
 * enabled per session by {@code TenantFilterAspect} in {@code dcc-application}.
 */
public final class TenantFilter {

    /** Filter name shared by the {@code @FilterDef} and every {@code @Filter}. */
    public static final String NAME = "tenantFilter";

    /** Parameter carrying the current tenant id. */
    public static final String PARAM = "tenantId";

    private TenantFilter() {
    }
}
