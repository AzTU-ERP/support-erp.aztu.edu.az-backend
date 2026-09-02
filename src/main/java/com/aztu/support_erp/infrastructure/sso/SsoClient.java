package com.aztu.support_erp.infrastructure.sso;

import com.aztu.support_erp.security.SsoUser;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Validates SSO tokens issued by the central auth microservice.
 * If an introspection URL is configured it is called remotely; otherwise a local
 * dev decoder accepts base64("userId:role1,role2[:full name[:email]]") tokens.
 * No passwords are stored.
 */
@Component
public class SsoClient {

    private static final Logger log = LoggerFactory.getLogger(SsoClient.class);

    private final String introspectionUrl;
    private final boolean devMode;
    private final RestClient restClient = RestClient.create();

    public SsoClient(@Value("${app.sso.introspection-url:}") String introspectionUrl,
                     @Value("${app.sso.dev-mode:true}") boolean devMode) {
        this.introspectionUrl = introspectionUrl;
        this.devMode = devMode;
    }

    public Optional<SsoUser> validate(String token) {
        if (token == null || token.isBlank()) return Optional.empty();
        if (introspectionUrl != null && !introspectionUrl.isBlank()) {
            return validateRemote(token);
        }
        if (devMode) {
            return decodeDev(token);
        }
        return Optional.empty();
    }

    @SuppressWarnings("unchecked")
    private Optional<SsoUser> validateRemote(String token) {
        try {
            Map<String, Object> body = restClient.get()
                    .uri(introspectionUrl)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .body(Map.class);
            if (body == null) return Optional.empty();
            Object active = body.get("active");
            if (active instanceof Boolean b && !b) return Optional.empty();
            UUID userId = UUID.fromString(String.valueOf(body.getOrDefault("user_id", body.get("sub"))));
            Object rolesRaw = body.getOrDefault("roles", body.get("authorities"));
            Set<String> roles = new LinkedHashSet<>();
            if (rolesRaw instanceof Iterable<?> it) {
                it.forEach(r -> roles.add(String.valueOf(r)));
            }
            String fullName = str(body.getOrDefault("full_name", body.get("name")));
            String email = str(body.get("email"));
            return Optional.of(new SsoUser(userId, fullName, email, roles));
        } catch (Exception ex) {
            log.warn("SSO introspection failed: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    private Optional<SsoUser> decodeDev(String token) {
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
            String[] parts = decoded.split(":", 4);
            UUID userId = UUID.fromString(parts[0].trim());
            Set<String> roles = parts.length > 1
                    ? Arrays.stream(parts[1].split(",")).map(String::trim).filter(s -> !s.isEmpty())
                        .collect(Collectors.toCollection(LinkedHashSet::new))
                    : Set.of();
            String fullName = parts.length > 2 && !parts[2].isBlank() ? parts[2].trim() : null;
            String email = parts.length > 3 && !parts[3].isBlank() ? parts[3].trim() : null;
            return Optional.of(new SsoUser(userId, fullName, email, roles));
        } catch (Exception ex) {
            log.debug("Dev SSO token decode failed: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    private static String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
