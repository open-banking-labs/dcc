package cn.org.openbanking.dcc.warmup.spi;

/**
 * An in-memory cache (map, list, ...) that wants to be populated eagerly at
 * startup. Implement it as a Spring bean and the {@code warmup-jvm-cache} step
 * warms every {@code WarmableCache} it finds.
 */
public interface WarmableCache {

    /** Cache name, for logs. */
    String name();

    /** Populate the cache. May throw. */
    void warm();
}
