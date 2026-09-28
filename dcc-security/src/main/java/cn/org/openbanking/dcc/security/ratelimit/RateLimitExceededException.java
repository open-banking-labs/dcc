package cn.org.openbanking.dcc.security.ratelimit;

/**
 * Thrown when an {@link OperationRateLimit} is exceeded. Map it to HTTP 429 at
 * the exposure layer.
 */
public class RateLimitExceededException extends RuntimeException {

    public RateLimitExceededException(String message) {
        super(message);
    }
}
