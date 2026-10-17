package cn.org.openbanking.dcc.core.common.error;

/**
 * Raised when an operation targets a tenant that is disabled or logically deleted.
 * The token's tenant claim is trusted at the edge, but a tenant can be turned off
 * after a token was issued; the use-case layer revalidates the registry.
 */
public class TenantInactiveException extends DomainException {

    public TenantInactiveException(String tenantId) {
        super("tenant '" + tenantId + "' is disabled or deleted");
    }
}
