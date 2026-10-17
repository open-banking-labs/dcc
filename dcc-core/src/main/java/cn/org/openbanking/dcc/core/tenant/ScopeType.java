package cn.org.openbanking.dcc.core.tenant;

/**
 * Operating surface a directory grant is valid for (ADR 0004). {@link #TENANT} is the
 * historical tenant-wide semantics; the other scopes narrow the grant to one
 * environment / one application, addressed by their tenant-unique business code.
 */
public enum ScopeType {

    /** Tenant-wide: the grant applies to every request in the tenant. */
    TENANT,

    /** Grant applies only when the request declares this environment as its surface. */
    ENVIRONMENT,

    /** Grant applies only when the request declares this application as its surface. */
    APPLICATION
}
