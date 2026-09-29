package cn.org.openbanking.dcc.security;

import org.springframework.stereotype.Component;

/**
 * Exposes the configured authority names as a bean, so {@code @PreAuthorize} can
 * reference them without hard-coding literals:
 *
 * <pre>
 * &#64;PreAuthorize("hasAuthority(@dccAuthorities.admin)")
 * </pre>
 *
 * The values come from {@code dcc.security.authorities.*}; the defaults reproduce
 * the historical {@code DCC_ADMIN} / {@code DCC_APPROVER} / {@code DCC_USER} names.
 */
@Component("dccAuthorities")
public class DccAuthorities {

    private final DccSecurityProperties properties;

    public DccAuthorities(DccSecurityProperties properties) {
        this.properties = properties;
    }

    /** Authority required for administrative operations (default {@code DCC_ADMIN}). */
    public String getAdmin() {
        return authority("admin", "DCC_ADMIN");
    }

    /** Authority required to approve/review (default {@code DCC_APPROVER}). */
    public String getApprover() {
        return authority("approver", "DCC_APPROVER");
    }

    /** Baseline authenticated-user authority (default {@code DCC_USER}). */
    public String getUser() {
        return authority("user", "DCC_USER");
    }

    private String authority(String key, String fallback) {
        String value = properties.getAuthorities().get(key);
        return value != null ? value : fallback;
    }
}
