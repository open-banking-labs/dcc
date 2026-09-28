package cn.org.openbanking.dcc.web.common;

import cn.org.openbanking.dcc.security.TenantContext;

/**
 * Base for controllers whose operations are scoped to the authenticated caller's
 * tenant. The tenant is taken from the shared security context established by
 * {@code dcc-security}; it is never read from the request body.
 */
public abstract class AbstractTenantController {

    protected String currentTenantId() {
        return TenantContext.get();
    }
}
