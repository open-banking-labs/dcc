package cn.org.openbanking.dcc.application.common;

import java.util.function.Supplier;

import org.springframework.stereotype.Component;

/**
 * Marks the current thread as executing an environment migration, carrying the
 * provenance (source environment + version). While active:
 * <ul>
 *   <li>the direct-maintenance guard is bypassed (the target environment accepts
 *       migration writes); and</li>
 *   <li>the target artifact is the migration target, not a user edit.</li>
 * </ul>
 */
@Component
public class MigrationContext {

    private static final ThreadLocal<Origin> CURRENT = new ThreadLocal<>();

    public record Origin(Long sourceEnvironmentId, String sourceVersion) {
    }

    public boolean active() {
        return CURRENT.get() != null;
    }

    public Long sourceEnvironmentId() {
        Origin origin = CURRENT.get();
        return origin == null ? null : origin.sourceEnvironmentId();
    }

    public String sourceVersion() {
        Origin origin = CURRENT.get();
        return origin == null ? null : origin.sourceVersion();
    }

    public <T> T call(Long sourceEnvironmentId, String sourceVersion, Supplier<T> action) {
        Origin previous = CURRENT.get();
        CURRENT.set(new Origin(sourceEnvironmentId, sourceVersion));
        try {
            return action.get();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }

    public void run(Long sourceEnvironmentId, String sourceVersion, Runnable action) {
        call(sourceEnvironmentId, sourceVersion, () -> {
            action.run();
            return null;
        });
    }
}