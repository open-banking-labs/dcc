package cn.org.openbanking.dcc.warmup.task;

import java.util.List;

import cn.org.openbanking.dcc.warmup.spi.WarmableCache;
import cn.org.openbanking.dcc.warmup.spi.WarmupTask;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Warms the in-memory caches registered as {@link WarmableCache} beans.
 *
 * <p>Registered as {@code warmup-jvm-cache}.
 */
@Component
public class JvmCacheWarmupTask implements WarmupTask {

    private static final Logger log = LoggerFactory.getLogger(JvmCacheWarmupTask.class);

    private final List<WarmableCache> caches;

    public JvmCacheWarmupTask(List<WarmableCache> caches) {
        this.caches = caches;
    }

    @Override
    public String name() {
        return "warmup-jvm-cache";
    }

    @Override
    public void warmUp() {
        if (caches.isEmpty()) {
            log.info("No WarmableCache beans to warm");
            return;
        }
        for (WarmableCache cache : caches) {
            cache.warm();
            log.info("JVM cache '{}' warmed", cache.name());
        }
    }
}
