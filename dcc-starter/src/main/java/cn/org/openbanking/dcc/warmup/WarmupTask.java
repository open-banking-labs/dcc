package cn.org.openbanking.dcc.warmup;

import org.springframework.core.Ordered;

/**
 * A single warm-up step.
 *
 * <p>Implement it as a Spring bean; every {@code WarmupTask} bean found is run by
 * {@link WarmupApplicationRunner}. Tasks run in the order given by
 * {@link org.springframework.core.annotation.Order @Order} / {@link Ordered}
 * (lower values first), so a task can declare its own position relative to the
 * others.
 */
public interface WarmupTask {

    /** Warm-up logic. May throw; the runner decides what to do on failure. */
    void warmUp() throws Exception;

    /** Name used in logs; defaults to the implementation class' simple name. */
    default String name() {
        return getClass().getSimpleName();
    }
}
