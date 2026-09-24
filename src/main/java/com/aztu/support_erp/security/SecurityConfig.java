package com.aztu.support_erp.security;

import com.aztu.support_erp.infrastructure.sso.SsoClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * SSO-based stateless security. Login lives in the central auth service — this module only
 * consumes the issued token and gates each endpoint on the roles it carries.
 *
 * <p>Matchers are declared most-specific-first because Spring Security evaluates them in order.
 * The split that matters here is "my own tickets" (any authenticated account) versus the queue
 * (DEV only); ownership of an individual row is re-checked in the service, which is the only
 * place that can see who filed it.
 */
@Configuration
public class SecurityConfig {

    private static final String DEV = "dev";

    private final SsoClient ssoClient;
    private final CorsConfigurationSource corsConfigurationSource;
    private final String serviceToken;

    public SecurityConfig(SsoClient ssoClient,
                          CorsConfigurationSource corsConfigurationSource,
                          @Value("${app.auth.service-token:}") String serviceToken) {
        this.ssoClient = ssoClient;
        this.corsConfigurationSource = corsConfigurationSource;
        this.serviceToken = serviceToken;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource))
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .requestMatchers("/error", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()

                // ---- service-to-service (the auth service asking about an account) ----
                // An authority rather than a role, so no SSO role name can ever satisfy it —
                // see ServiceTokenFilter.AUTHORITY.
                .requestMatchers("/api/support/internal/**").hasAuthority(ServiceTokenFilter.AUTHORITY)

                // ---- identity and the reportable surface ----
                .requestMatchers(HttpMethod.GET, "/api/support/me").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/support/me/violation-status").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/support/config/**").authenticated()

                // ---- anyone signed in may report a problem and follow their own reports ----
                .requestMatchers(HttpMethod.POST, "/api/support/tickets").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/support/tickets/my", "/api/support/tickets/my/*")
                    .authenticated()
                // Screenshots belong to whoever filed the ticket; the service checks that before
                // streaming the bytes, so reporters and DEVs can share one route.
                .requestMatchers(HttpMethod.GET, "/api/support/tickets/*/attachments/*").authenticated()

                // ---- everything else about tickets is the DEV queue ----
                .requestMatchers("/api/support/tickets/**").hasRole(DEV)
                .requestMatchers("/api/support/violations/**").hasRole(DEV)
                .requestMatchers("/api/support/devs").hasRole(DEV)

                .requestMatchers("/api/**").authenticated()
                .anyRequest().permitAll())
            .addFilterBefore(new ServiceTokenFilter(serviceToken), UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(new SsoAuthenticationFilter(ssoClient), UsernamePasswordAuthenticationFilter.class)
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((req, res, e) -> write(res, HttpStatus.UNAUTHORIZED, "Authentication is required"))
                .accessDeniedHandler((req, res, e) -> write(res, HttpStatus.FORBIDDEN, "You are not authorized to perform this action")));
        return http.build();
    }

    private void write(jakarta.servlet.http.HttpServletResponse res, HttpStatus status, String message) throws java.io.IOException {
        res.setStatus(status.value());
        res.setContentType("application/json");
        res.setCharacterEncoding("UTF-8");
        String safe = message.replace("\\", "\\\\").replace("\"", "\\\"");
        res.getWriter().write("{\"success\":false,\"message\":\"" + safe + "\",\"data\":null}");
    }
}
