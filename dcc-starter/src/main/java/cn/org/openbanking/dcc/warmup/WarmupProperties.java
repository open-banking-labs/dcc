package cn.org.openbanking.dcc.warmup;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the application warm-up, bound from {@code dcc.warmup.*}.
 *
 * <pre>
 * dcc:
 *   warmup:
 *     enabled: true     # run the warm-up at all
 *     order: 0          # position of the warm-up runner among ApplicationRunners
 *     async: false      # run off the startup thread
 *     fail-fast: true   # true = stop the application on failure, false = ignore it
 * </pre>
 */
@ConfigurationProperties(prefix = "dcc.warmup")
public class WarmupProperties {

    /** Whether to run the warm-up tasks at all. */
    private boolean enabled = true;

    /** Order of the warm-up {@link org.springframework.boot.ApplicationRunner} relative to other runners. */
    private int order = 0;

    /** Run the warm-up asynchronously, off the application-startup thread. */
    private boolean async = false;

    /** On failure: {@code true} stops the application, {@code false} logs and continues. */
    private boolean failFast = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getOrder() {
        return order;
    }

    public void setOrder(int order) {
        this.order = order;
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
}
