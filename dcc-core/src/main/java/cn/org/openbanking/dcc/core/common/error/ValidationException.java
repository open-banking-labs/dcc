package cn.org.openbanking.dcc.core.common.error;

/**
 * Raised when input violates a domain rule (not a bean-validation constraint):
 * a missing required reference, an empty version diff, an unsupported value, and
 * similar.
 */
public class ValidationException extends DomainException {

    public ValidationException(String message) {
        super(message);
    }
}
