package cn.org.openbanking.dcc.core.common.tenant;

/**
 * Holds the tenant of the work currently executing on this thread.
 *
 * <p>{@code dcc-security} decides the tenant during authentication and exposes
 * it via its own {@code TenantContext}; the exposure layers pass that tenant
 * into the use-case layer, which binds it here for the duration of the call so
 * persistence and the Hibernate {@code tenantFilter} can scope reads and writes
 * without threading it through every internal method.
 *
 * <p>Always {@link #clear()} when you set it on a thread that outlives the work
 * (see {@code TenantScope} in {@code dcc-application}).
 */
public final class CurrentTenant {

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private CurrentTenant() {
    }

    /** Binds the tenant for the current thread (may be {@code null} to clear). */
    public static void set(String tenantId) {
        CURRENT.set(tenantId);
    }

    /** @return the tenant bound to the current thread, or {@code null}. */
    public static String get() {
        return CURRENT.get();
    }

    /** Unbinds the tenant from the current thread. */
    public static void clear() {
        CURRENT.remove();
    }
}
