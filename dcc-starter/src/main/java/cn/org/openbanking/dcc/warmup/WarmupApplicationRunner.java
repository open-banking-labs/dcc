package cn.org.openbanking.dcc.warmup;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

/**
 * Runs the registered {@link WarmupTask}s once the application context is ready.
 *
 * <p>All behaviour is driven by {@link WarmupProperties}:
 * <ul>
 *   <li>{@code enabled} - whether the warm-up runs at all;</li>
 *   <li>{@code order} - this runner's position among the other
 *       {@link ApplicationRunner}s;</li>
 *   <li>{@code async} - run off the startup thread instead of blocking it;</li>
 *   <li>{@code fail-fast} - on failure, stop the application ({@code true}) or
 *       just log and carry on ({@code false}).</li>
 * </ul>
 */
@Component
public class WarmupApplicationRunner implements ApplicationRunner, Ordered {

    private static final Logger log = LoggerFactory.getLogger(WarmupApplicationRunner.class);

    private final WarmupProperties properties;
    private final List<WarmupTask> tasks;
    private final ConfigurableApplicationContext context;
    private final TaskExecutor executor;

    public WarmupApplicationRunner(WarmupProperties properties,
                                   List<WarmupTask> tasks,
                                   ConfigurableApplicationContext context) {
        this.properties = properties;
        this.tasks = tasks;
        this.context = context;
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("warmup-");
        executor.setVirtualThreads(false);
        this.executor = executor;
    }

    /** Position of this runner, taken from {@code dcc.warmup.order}. */
    @Override
    public int getOrder() {
        return properties.getOrder();
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isEnabled()) {
            log.info("Application warm-up is disabled (dcc.warmup.enabled=false)");
            return;
        }
        if (tasks.isEmpty()) {
            log.info("Application warm-up is enabled but no WarmupTask beans are registered");
            return;
        }
        log.info("Application warm-up: {} task(s), mode={}, on failure={}",
                tasks.size(),
                properties.isAsync() ? "async" : "sync",
                properties.isFailFast() ? "stop" : "ignore");

        if (properties.isAsync()) {
            executor.execute(() -> {
                try {
                    executeTasks();
                    log.info("Application warm-up finished (async)");
                } catch (Exception ex) {
                    log.error("Asynchronous warm-up failed; stopping the application", ex);
                    stopApplication();
                }
            });
        } else {
            executeTasks();
        }
    }

    /**
     * Runs every task in order. When {@code fail-fast} is on, the first failure
     * aborts the sequence by rethrowing; otherwise it is logged and skipped.
     */
    private void executeTasks() {
        for (WarmupTask task : tasks) {
            try {
                task.warmUp();
                log.info("Warm-up task '{}' completed", task.name());
            } catch (Exception ex) {
                if (properties.isFailFast()) {
                    throw new IllegalStateException("Warm-up task '" + task.name() + "' failed", ex);
                }
                log.warn("Warm-up task '{}' failed and was ignored (dcc.warmup.fail-fast=false)",
                        task.name(), ex);
            }
        }
    }

    private void stopApplication() {
        try {
            context.close();
        } catch (Exception ex) {
            log.error("Failed to stop the application after a warm-up failure", ex);
        }
    }
}
