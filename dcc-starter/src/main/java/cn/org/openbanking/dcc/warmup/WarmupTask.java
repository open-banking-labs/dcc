package cn.org.openbanking.dcc.warmup;

/**
 * A single warm-up step.
 *
 * <p>Implement it as a Spring bean and list its {@link #name()} in
 * {@code dcc.warmup.steps}; that configuration is what defines the run order.
 * Only the tasks named there are run (a task that is not listed is skipped).
 */
public interface WarmupTask {

    /** Warm-up logic. May throw; the runner decides what to do on failure. */
    void warmUp() throws Exception;

    /**
     * Stable name that identifies this step in {@code dcc.warmup.steps}; defaults
     * to the implementation class' simple name.
     */
    default String name() {
        return getClass().getSimpleName();
    }
}
