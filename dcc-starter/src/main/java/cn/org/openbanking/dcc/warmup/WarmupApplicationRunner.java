package cn.org.openbanking.dcc.warmup;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

/**
 * Runs the configured warm-up steps once the application context is ready.
 *
 * <p>Everything is driven by {@link WarmupProperties}:
 * <ul>
 *   <li>{@code enabled} - whether the warm-up runs at all;</li>
 *   <li>{@code steps} - the ordered list of {@link WarmupTask#name() names} to
 *       run; this is what defines each step's order;</li>
 *   <li>{@code async} - run off the startup thread instead of blocking it;</li>
 *   <li>{@code fail-fast} - on failure, shut the application down and exit the
 *       process ({@code true}) or just log and carry on ({@code false}).</li>
 * </ul>
 */
@Component
public class WarmupApplicationRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(WarmupApplicationRunner.class);

    /** Exit code used when the warm-up fails and {@code fail-fast} is on. */
    private static final int WARMUP_FAILURE_EXIT_CODE = 1;

    private final WarmupProperties properties;
    private final Map<String, WarmupTask> tasksByName;
    private final ConfigurableApplicationContext context;
    private final TaskExecutor executor;

    public WarmupApplicationRunner(WarmupProperties properties,
                                   List<WarmupTask> tasks,
                                   ConfigurableApplicationContext context) {
        this.properties = properties;
        this.context = context;
        this.tasksByName = indexByName(tasks);
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("warmup-");
        executor.setVirtualThreads(false);
        this.executor = executor;
    }

    private static Map<String, WarmupTask> indexByName(List<WarmupTask> tasks) {
        Map<String, WarmupTask> byName = new LinkedHashMap<>();
        for (WarmupTask task : tasks) {
            WarmupTask previous = byName.putIfAbsent(task.name(), task);
            if (previous != null) {
                throw new IllegalStateException("Duplicate WarmupTask name '" + task.name() + "': "
                        + previous.getClass().getName() + " and " + task.getClass().getName());
            }
        }
        return byName;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isEnabled()) {
            log.info("Application warm-up is disabled (dcc.warmup.enabled=false)");
            return;
        }
        List<String> steps = properties.getSteps();
        if (steps.isEmpty()) {
            log.info("Application warm-up is enabled but dcc.warmup.steps is empty");
            return;
        }
        warnAboutUnconfiguredTasks(steps);

        log.info("Application warm-up: {} step(s) [{}], mode={}, on failure={}",
                steps.size(), String.join(" -> ", steps),
                properties.isAsync() ? "async" : "sync",
                properties.isFailFast() ? "shutdown + exit" : "ignore");

        if (properties.isAsync()) {
            executor.execute(this::executeSteps);
        } else {
            executeSteps();
        }
    }

    private void warnAboutUnconfiguredTasks(List<String> steps) {
        tasksByName.keySet().stream()
                .filter(name -> !steps.contains(name))
                .forEach(name -> log.warn("WarmupTask '{}' is registered but not listed in "
                        + "dcc.warmup.steps; it will not run", name));
    }

    /** Runs the configured steps in order. */
    private void executeSteps() {
        for (String stepName : properties.getSteps()) {
            WarmupTask task = tasksByName.get(stepName);
            if (task == null) {
                if (handleFailure("step '" + stepName + "' is configured but no WarmupTask has that name", null)) {
                    return;
                }
                continue;
            }
            try {
                task.warmUp();
                log.info("Warm-up step '{}' completed", stepName);
            } catch (Exception ex) {
                if (handleFailure("step '" + stepName + "' failed", ex)) {
                    return;
                }
            }
        }
        log.info("Application warm-up finished");
    }

    /**
     * Handles a failed step. Returns {@code true} when the sequence must stop
     * (fail-fast), {@code false} when the failure is only ignored.
     */
    private boolean handleFailure(String message, Exception cause) {
        if (properties.isFailFast()) {
            log.error("Application warm-up failed ({}) - shutting down and exiting", message, cause);
            shutdownAndExit();
            return true;
        }
        log.warn("Application warm-up failure ignored ({}); continuing (dcc.warmup.fail-fast=false)",
                message, cause);
        return false;
    }

    /** Gracefully context-close and exit the JVM; overridable so tests need not exit. */
    protected void shutdownAndExit() {
        int exitCode = SpringApplication.exit(context, () -> WARMUP_FAILURE_EXIT_CODE);
        System.exit(exitCode);
    }
}
