package cn.org.openbanking.dcc.security;

/**
 * Holds the tenant of the current request for the duration of that request, so
 * persistence and business code can scope reads and writes without threading the
 * tenant through every method signature.
 *
 * <p>Set and cleared by the internal-assertion filter. Always {@link #clear()}
 * when you set it on a thread that outlives the request (e.g. async work).
 */
public final class TenantContext {

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    /** Sets the current tenant (may be {@code null} to clear). */
    public static void set(String tenant) {
        CURRENT.set(tenant);
    }

    /** @return the current tenant, or {@code null} when none is set. */
    public static String get() {
        return CURRENT.get();
    }

    /** Removes the tenant from the current thread. */
    public static void clear() {
        CURRENT.remove();
    }
}
