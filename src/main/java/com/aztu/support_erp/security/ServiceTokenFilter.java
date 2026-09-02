package com.aztu.support_erp.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates the auth service on {@code /api/support/internal/**} by a shared secret.
 *
 * <p>These endpoints answer questions about accounts ("is this user blocked?") for a caller that
 * holds no SSO token of its own, so the bearer filter has nothing to work with. A single header
 * is enough because the endpoints are read-only, carry no personal data beyond a flag, and are
 * expected to sit inside the cluster network.
 */
public class ServiceTokenFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Service-Token";
    private static final String PATH_PREFIX = "/api/support/internal/";

    private final String expectedToken;

    public ServiceTokenFilter(String expectedToken) {
        this.expectedToken = expectedToken;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getRequestURI().startsWith(PATH_PREFIX) && matches(request.getHeader(HEADER))) {
            SecurityContextHolder.getContext().setAuthentication(
                    UsernamePasswordAuthenticationToken.authenticated("auth-service", null,
                            List.of(new SimpleGrantedAuthority("ROLE_service"))));
        }
        chain.doFilter(request, response);
    }

    /** Constant-time so the comparison cannot be used to recover the secret a byte at a time. */
    private boolean matches(String presented) {
        if (presented == null || expectedToken == null || expectedToken.isBlank()) return false;
        return MessageDigest.isEqual(
                presented.getBytes(StandardCharsets.UTF_8),
                expectedToken.getBytes(StandardCharsets.UTF_8));
    }
}
