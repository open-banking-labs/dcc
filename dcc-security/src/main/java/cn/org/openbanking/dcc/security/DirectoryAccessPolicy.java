package cn.org.openbanking.dcc.security;

import java.util.Set;

/**
 * Seam for directory-backed identity governance ({@code dcc.security.directory-mode}).
 * Implemented by the application layer (which owns the JPA repositories) and consulted
 * by {@link BearerJwtAuthenticationFilter} right after the token is verified, so
 * membership and tenant-assigned roles are decided in exactly one place, before any
 * use case runs.
 *
 * <p>Semantics are deliberately narrow:
 * <ul>
 *   <li>{@link #isMember} <em>gates</em>: a verified token whose subject has no live
 *       membership in the claimed tenant is rejected outright (403).</li>
 *   <li>{@link #assignedRoles} <em>adds</em>: granted roles are unioned with the token's
 *       claim roles before authority mapping. The directory never removes what the
 *       identity provider already grants.</li>
 * </ul>
 */
public interface DirectoryAccessPolicy {

    /** @return true when the subject may act inside the tenant (live membership). */
    boolean isMember(String subject, String tenantId);

    /** @return tenant-wide roles granted to the subject (never null). */
    Set<String> assignedRoles(String subject, String tenantId);

    /**
     * Returns roles for one explicitly selected operating surface. Implementations
     * must verify that {@code targetId} belongs to {@code tenantId} and has
     * {@code scopeCode}; the request header alone is never trusted.
     */
    Set<String> assignedRoles(String subject, String tenantId, String scopeType, String scopeCode, Long targetId);
}
