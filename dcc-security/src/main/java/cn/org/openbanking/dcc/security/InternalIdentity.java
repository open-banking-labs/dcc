package cn.org.openbanking.dcc.security;

import java.util.Set;

/**
 * The authenticated caller as asserted by the edge gateway: the subject, the
 * tenant it belongs to, and its roles. It is the principal put into the Spring
 * Security context and the source of the {@link TenantContext}.
 */
public record InternalIdentity(String subject, String tenant, Set<String> roles) {

    public InternalIdentity {
        roles = roles == null ? Set.of() : Set.copyOf(roles);
    }
}
