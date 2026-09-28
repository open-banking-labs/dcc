package cn.org.openbanking.dcc.core.common.error;

/**
 * Raised when an operation violates a uniqueness or state invariant, e.g. a
 * duplicate code within the same tenant/environment/application, or an illegal
 * status transition.
 */
public class ConflictException extends DomainException {

    public ConflictException(String message) {
        super(message);
    }
}
