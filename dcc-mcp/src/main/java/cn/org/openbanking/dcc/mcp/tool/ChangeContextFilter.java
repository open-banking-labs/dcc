package cn.org.openbanking.dcc.mcp.tool;

import java.io.IOException;

import cn.org.openbanking.dcc.application.common.ChangeContext;
import cn.org.openbanking.dcc.security.InternalIdentity;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * MCP-side twin of {@code dcc-web}'s ChangeContextFilter: populates the who/why
 * provenance for artifact changes made through MCP tools.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class ChangeContextFilter extends OncePerRequestFilter {

    private static final String REASON_HEADER = "X-DCC-Change-Reason";

    private final ChangeContext changeContext;

    public ChangeContextFilter(ChangeContext changeContext) {
        this.changeContext = changeContext;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String author = authentication != null && authentication.getPrincipal() instanceof InternalIdentity identity
                ? identity.subject()
                : null;
        changeContext.set(author, request.getHeader(REASON_HEADER));
        try {
            chain.doFilter(request, response);
        } finally {
            changeContext.clear();
        }
    }
}
