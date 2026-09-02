package com.aztu.support_erp.authsync.service;

import com.aztu.support_erp.authsync.domain.AuthSyncEvent;
import com.aztu.support_erp.authsync.repository.AuthSyncEventRepository;
import com.aztu.support_erp.common.enums.AuthSyncStatus;
import com.aztu.support_erp.common.enums.AuthSyncType;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Queues block/unblock decisions for the auth service.
 *
 * <p>Enqueuing joins the caller's transaction on purpose: the outbox row and the decision that
 * produced it commit together, so the auth service can never be told about a block that was
 * rolled back, nor miss one that stuck. Delivery is somebody else's problem — {@link AuthSyncWorker}
 * picks the row up on its next pass.
 */
@Service
public class AuthSyncService {

    private static final Logger log = LoggerFactory.getLogger(AuthSyncService.class);

    private final AuthSyncEventRepository repository;

    public AuthSyncService(AuthSyncEventRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueueBlock(UUID ssoUserId, String reason) {
        enqueue(AuthSyncType.BLOCK, ssoUserId, reason);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueueUnblock(UUID ssoUserId, String reason) {
        enqueue(AuthSyncType.UNBLOCK, ssoUserId, reason);
    }

    private void enqueue(AuthSyncType type, UUID ssoUserId, String reason) {
        AuthSyncEvent event = new AuthSyncEvent();
        event.setEventType(type.code());
        event.setSsoUserId(ssoUserId);
        event.setPayload(payload(type, ssoUserId, reason));
        event.setStatus(AuthSyncStatus.PENDING.code());
        repository.save(event);
        log.info("Queued {} for SSO user {}", type.code(), ssoUserId);
    }

    /**
     * The body the auth service receives. Hand-built rather than serialised: it is three fields
     * that must not drift, and the only free text is escaped here.
     */
    private static String payload(AuthSyncType type, UUID ssoUserId, String reason) {
        return "{\"userId\":\"" + ssoUserId + "\","
                + "\"action\":\"" + type.code() + "\","
                + "\"reason\":\"" + escape(reason) + "\","
                + "\"source\":\"support-erp\"}";
    }

    private static String escape(String value) {
        if (value == null) return "";
        StringBuilder out = new StringBuilder(value.length());
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }
}
