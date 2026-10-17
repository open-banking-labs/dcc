package cn.org.openbanking.dcc.security;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.lang.Nullable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates the caller from the {@code Authorization: Bearer <jwt>} header:
 * verifies the token, builds an {@link InternalIdentity} (subject, tenant, roles)
 * as the principal, and scopes the request to its tenant via {@link TenantContext}.
 *
 * <p>The exposure layers are only reachable through the gateway, but the token
 * itself is authoritative here - the gateway does not need to validate it, so
 * "who the caller is" is decided in exactly one place (this shared module).
 *
 * <p>In directory mode ({@code dcc.security.directory-mode}), the verified token is
 * only the identity: the {@link DirectoryAccessPolicy} additionally gates the
 * subject/tenant pair (a 403 {@code not_a_directory_member} when no membership)
 * and unions tenant-assigned roles with the claim roles before authority mapping.
 */
public class BearerJwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtDecoder jwtDecoder;
    private final DccSecurityProperties properties;
    private final RolePermissionResolver rolePermissionResolver;
    /** Only populated in directory mode; may be {@code null} otherwise. */
    private final @Nullable DirectoryAccessPolicy directoryPolicy;

    public BearerJwtAuthenticationFilter(JwtDecoder jwtDecoder, DccSecurityProperties properties,
            RolePermissionResolver rolePermissionResolver) {
        this(jwtDecoder, properties, rolePermissionResolver, null);
    }

    public BearerJwtAuthenticationFilter(JwtDecoder jwtDecoder, DccSecurityProperties properties,
            RolePermissionResolver rolePermissionResolver, @Nullable DirectoryAccessPolicy directoryPolicy) {
        this.jwtDecoder = jwtDecoder;
        this.properties = properties;
        this.rolePermissionResolver = rolePermissionResolver;
        this.directoryPolicy = directoryPolicy;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            try {
                Jwt jwt = jwtDecoder.decode(header.substring(BEARER_PREFIX.length()));
                List<String> roles = jwt.getClaimAsStringList(properties.getRolesClaim());
                Set<String> roleSet = roles == null ? Set.of() : new HashSet<>(roles);
                String subject = jwt.getSubject();
                String tenant = jwt.getClaimAsString(properties.getTenantClaim());
                if (properties.isDirectoryMode()) {
                    // claim-only identity: the directory decides membership and adds tenant roles
                    if (!StringUtils.hasText(subject) || !StringUtils.hasText(tenant)) {
                        reject(response,
                                "directory_identity_required",
                                "directory mode requires the subject and tenant claims");
                        return;
                    }
                    if (!directoryPolicy.isMember(subject, tenant)) {
                        reject(response, "not_a_directory_member",
                                "subject '" + subject + "' has no membership in tenant '" + tenant + "'");
                        return;
                    }
                    roleSet = new HashSet<>(roleSet);
                    roleSet.addAll(directoryPolicy.assignedRoles(subject, tenant));
                }
                InternalIdentity identity = new InternalIdentity(subject, tenant, roleSet);
                var authorities = rolePermissionResolver.authoritiesFor(roleSet).stream()
                        .map(SimpleGrantedAuthority::new).toList();
                var authentication = new UsernamePasswordAuthenticationToken(identity, header, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
                TenantContext.set(identity.tenant());
            } catch (JwtException ex) {
                SecurityContextHolder.clearContext();
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"invalid_token\"}");
                return;
            }
        }
        try {
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    /** Directory rejections happen before authentication exists, so answer directly (403). */
    private static void reject(HttpServletResponse response, String code, String message) throws IOException {
        SecurityContextHolder.clearContext();
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"" + code + "\",\"message\":\"" + message + "\"}");
    }
}
