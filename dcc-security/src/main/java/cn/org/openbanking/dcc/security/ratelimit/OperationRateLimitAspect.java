package cn.org.openbanking.dcc.security.ratelimit;

import java.time.Duration;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Enforces {@link OperationRateLimit} with a fixed-window counter in Redis.
 *
 * <p>The first call in a window sets the key's TTL, so a window is a rolling
 * "first hit + windowSeconds"; later calls increment the same key. This is the
 * coarse, operation-level complement to the gateway's subject-level limiting.
 */
@Aspect
@Component
public class OperationRateLimitAspect {

    private static final String KEY_PREFIX = "dcc:ratelimit:";

    private final StringRedisTemplate redis;

    public OperationRateLimitAspect(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Around("@annotation(rateLimit)")
    public Object enforce(ProceedingJoinPoint joinPoint, OperationRateLimit rateLimit) throws Throwable {
        String key = KEY_PREFIX
                + (rateLimit.key().isEmpty() ? joinPoint.getSignature().toShortString() : rateLimit.key());
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redis.expire(key, Duration.ofSeconds(rateLimit.windowSeconds()));
        }
        if (count != null && count > rateLimit.limit()) {
            throw new RateLimitExceededException(
                    "Operation rate limit exceeded for '" + key + "' (" + rateLimit.limit()
                            + " per " + rateLimit.windowSeconds() + "s)");
        }
        return joinPoint.proceed();
    }
}
