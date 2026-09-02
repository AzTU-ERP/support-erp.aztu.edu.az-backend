package com.aztu.support_erp.authsync.service;

import com.aztu.support_erp.authsync.domain.AuthSyncEvent;
import com.aztu.support_erp.authsync.repository.AuthSyncEventRepository;
import com.aztu.support_erp.common.enums.AuthSyncStatus;
import com.aztu.support_erp.common.enums.AuthSyncType;
import com.aztu.support_erp.security.ServiceTokenFilter;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

/**
 * Polls auth_sync_events (pending/failed) and delivers each block/unblock to the auth service,
 * tracking attempts, last_error and sent_at. Honours a max-attempts ceiling.
 *
 * <p>Modelled on hr-erp's IntegrationOutboxWorker. With {@code app.auth.base-url} unset — the
 * local default — nothing is delivered and the rows simply accumulate: the decision is still
 * recorded here, and this service's own
 * {@code GET /api/support/internal/users/{id}/block-status} answers for it in the meantime.
 */
@Component
public class AuthSyncWorker {

    private static final Logger log = LoggerFactory.getLogger(AuthSyncWorker.class);

    private final AuthSyncEventRepository repository;
    private final String authBaseUrl;
    private final String serviceToken;
    private final int maxAttempts;
    private final RestClient restClient = RestClient.create();

    public AuthSyncWorker(AuthSyncEventRepository repository,
                          @Value("${app.auth.base-url:}") String authBaseUrl,
                          @Value("${app.auth.service-token:}") String serviceToken,
                          @Value("${app.auth.max-attempts:5}") int maxAttempts) {
        this.repository = repository;
        this.authBaseUrl = authBaseUrl;
        this.serviceToken = serviceToken;
        this.maxAttempts = maxAttempts;
    }

    @Scheduled(fixedDelayString = "${app.auth.poll-interval-ms:60000}")
    @Transactional
    public void deliverPending() {
        if (authBaseUrl == null || authBaseUrl.isBlank()) {
            return; // No auth service configured — see the class comment.
        }
        List<AuthSyncEvent> due = repository.findByStatusInAndAttemptsLessThanOrderByCreatedAtAsc(
                List.of(AuthSyncStatus.PENDING.code(), AuthSyncStatus.FAILED.code()), maxAttempts);
        for (AuthSyncEvent event : due) {
            deliver(event);
        }
    }

    private void deliver(AuthSyncEvent event) {
        event.setAttempts(event.getAttempts() == null ? 1 : event.getAttempts() + 1);
        try {
            AuthSyncType type = AuthSyncType.valueOf(event.getEventType());
            restClient.post()
                    .uri(authBaseUrl + type.path(event.getSsoUserId()))
                    .header(ServiceTokenFilter.HEADER, serviceToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(event.getPayload())
                    .retrieve()
                    .toBodilessEntity();
            event.setStatus(AuthSyncStatus.SENT.code());
            event.setSentAt(LocalDateTime.now());
            event.setLastError(null);
            log.info("Delivered {} for SSO user {} to the auth service",
                    event.getEventType(), event.getSsoUserId());
        } catch (Exception ex) {
            event.setStatus(AuthSyncStatus.FAILED.code());
            event.setLastError(truncate(ex.getMessage()));
            log.warn("Delivery of {} for SSO user {} failed (attempt {}): {}",
                    event.getEventType(), event.getSsoUserId(), event.getAttempts(), ex.getMessage());
        }
        repository.save(event);
    }

    private String truncate(String s) {
        if (s == null) return null;
        return s.length() > 1000 ? s.substring(0, 1000) : s;
    }
}
