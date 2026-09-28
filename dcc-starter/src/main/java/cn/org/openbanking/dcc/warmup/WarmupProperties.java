package cn.org.openbanking.dcc.warmup;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the application warm-up, bound from {@code dcc.warmup.*}.
 *
 * <pre>
 * dcc:
 *   warmup:
 *     enabled: true          # run the warm-up at all
 *     async: false           # run off the startup thread
 *     fail-fast: true        # on failure, shut the application down and exit the process
 *     steps:                 # ordered list of WarmupTask names - the list order is the run order
 *       - load-reference-data
 *       - warm-cache
 * </pre>
 */
@ConfigurationProperties(prefix = "dcc.warmup")
public class WarmupProperties {

    /** Whether to run the warm-up at all. */
    private boolean enabled = true;

    /** Run the warm-up asynchronously, off the application-startup thread. */
    private boolean async = false;

    /** On failure: {@code true} stops the application, {@code false} logs and continues. */
    private boolean failFast = true;

    /**
     * Ordered list of {@link WarmupTask#name() task names} to run. The list order
     * is the execution order; only the tasks named here are run.
     */
    private List<String> steps = new ArrayList<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isAsync() {
        return async;
    }

    public void setAsync(boolean async) {
        this.async = async;
    }

    public boolean isFailFast() {
        return failFast;
    }

    public void setFailFast(boolean failFast) {
        this.failFast = failFast;
    }

    public List<String> getSteps() {
        return steps;
    }

    public void setSteps(List<String> steps) {
        this.steps = steps;
    }
}
