package com.aztu.support_erp.security;

import com.aztu.support_erp.infrastructure.sso.SsoClient;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Validates the SSO bearer token on each request and populates the SecurityContext with roles. */
public class SsoAuthenticationFilter extends OncePerRequestFilter {

    private final SsoClient ssoClient;

    public SsoAuthenticationFilter(SsoClient ssoClient) {
        this.ssoClient = ssoClient;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7).trim();
            Optional<SsoUser> user = ssoClient.validate(token);
            user.ifPresent(u -> {
                List<SimpleGrantedAuthority> authorities = u.roles().stream()
                        .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                        .toList();
                SecurityContextHolder.getContext().setAuthentication(
                        new SsoAuthenticationToken(u, authorities));
            });
        }
        chain.doFilter(request, response);
    }
}
