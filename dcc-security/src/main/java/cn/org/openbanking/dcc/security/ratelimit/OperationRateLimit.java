package cn.org.openbanking.dcc.security.ratelimit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Applies an operation-level rate limit to a method: at most {@link #limit()}
 * invocations per {@link #windowSeconds()} window, counted in Redis so the limit
 * is shared across instances.
 *
 * <p>Use it for expensive or abuse-prone operations (e.g. code generation) that
 * the gateway's coarse, subject-level limiting cannot see. The counter key
 * defaults to the method signature; set {@link #key()} to share or split a limit
 * explicitly.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface OperationRateLimit {

    /** Explicit counter key; defaults to the annotated method's signature. */
    String key() default "";

    /** Maximum invocations allowed within the window. */
    int limit();

    /** Window length, in seconds. */
    int windowSeconds() default 60;
}
