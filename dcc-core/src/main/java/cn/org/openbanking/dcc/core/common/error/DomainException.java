package cn.org.openbanking.dcc.core.common.error;

/**
 * Base type for domain-level failures raised by {@code dcc-core} and
 * {@code dcc-application}. Transport layers map the concrete sub-types to
 * HTTP/MCP error responses; the domain itself carries no transport semantics.
 */
public abstract class DomainException extends RuntimeException {

    protected DomainException(String message) {
        super(message);
    }

    protected DomainException(String message, Throwable cause) {
        super(message, cause);
    }
}
