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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates the caller from the {@code Authorization: Bearer <jwt>} header:
 * verifies the token, builds an {@link InternalIdentity} (subject, tenant, roles)
 * as the principal, and scopes the request to its tenant via {@link TenantContext}.
 *
 * <p>The exposure layers are only reachable through the gateway, but the token
 * itself is authoritative here - the gateway does not need to validate it, so
 * "who the caller is" is decided in exactly one place (this shared module).
 */
public class BearerJwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtDecoder jwtDecoder;
    private final DccSecurityProperties properties;
    private final RolePermissionResolver rolePermissionResolver;

    public BearerJwtAuthenticationFilter(JwtDecoder jwtDecoder, DccSecurityProperties properties,
            RolePermissionResolver rolePermissionResolver) {
        this.jwtDecoder = jwtDecoder;
        this.properties = properties;
        this.rolePermissionResolver = rolePermissionResolver;
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
                InternalIdentity identity = new InternalIdentity(
                        jwt.getSubject(), jwt.getClaimAsString(properties.getTenantClaim()), roleSet);
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
}
