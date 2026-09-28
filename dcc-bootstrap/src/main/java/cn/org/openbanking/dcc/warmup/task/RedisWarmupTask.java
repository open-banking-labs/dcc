package cn.org.openbanking.dcc.warmup.task;

import cn.org.openbanking.dcc.warmup.spi.WarmupTask;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

/**
 * Warms Redis by opening a connection and issuing a {@code PING}, so the client's
 * connection and authentication are exercised before the first real request.
 *
 * <p>Registered as {@code warmup-redis}. Enable it by adding that name to
 * {@code dcc.warmup.steps}, with Redis running.
 */
@Component
public class RedisWarmupTask implements WarmupTask {

    private static final Logger log = LoggerFactory.getLogger(RedisWarmupTask.class);

    private final RedisConnectionFactory connectionFactory;

    public RedisWarmupTask(RedisConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    @Override
    public String name() {
        return "warmup-redis";
    }

    @Override
    public void warmUp() {
        try (RedisConnection connection = connectionFactory.getConnection()) {
            String pong = connection.ping();
            log.info("Redis warmed (PING -> {})", pong);
        }
    }
}
