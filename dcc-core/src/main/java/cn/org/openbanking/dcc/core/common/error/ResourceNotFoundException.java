package cn.org.openbanking.dcc.core.common.error;

/**
 * Raised when a requested aggregate cannot be found inside the caller's tenant
 * scope. Never leak the existence of another tenant's data through this type.
 */
public class ResourceNotFoundException extends DomainException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public static ResourceNotFoundException of(String type, Object id) {
        return new ResourceNotFoundException(type + " " + id + " not found");
    }

    public static ResourceNotFoundException of(String type, String field, Object value) {
        return new ResourceNotFoundException(type + " with " + field + "=" + value + " not found");
    }
}
