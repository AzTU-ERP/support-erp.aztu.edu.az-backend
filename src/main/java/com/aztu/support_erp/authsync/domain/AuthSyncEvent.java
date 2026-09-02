package com.aztu.support_erp.authsync.domain;

import com.aztu.support_erp.common.BaseEntity;
import com.aztu.support_erp.common.enums.AuthSyncStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

/**
 * auth_sync_events — outbox row for the auth service. payload is JSONB; never write another
 * service's database directly.
 *
 * <p>Written in the same transaction as the block or unblock it describes, so the decision and
 * its delivery cannot disagree: if the auth service is down the row simply waits.
 */
@Entity
@Table(name = "auth_sync_events")
public class AuthSyncEvent extends BaseEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    /** The auth service's own id for the account — not our local users_ref id. */
    @Column(name = "sso_user_id", nullable = false)
    private UUID ssoUserId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(name = "status", nullable = false)
    private String status = AuthSyncStatus.PENDING.code();

    @Column(name = "attempts", nullable = false)
    private Integer attempts = 0;

    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public UUID getSsoUserId() { return ssoUserId; }
    public void setSsoUserId(UUID ssoUserId) { this.ssoUserId = ssoUserId; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getAttempts() { return attempts; }
    public void setAttempts(Integer attempts) { this.attempts = attempts; }
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
    public LocalDateTime getSentAt() { return sentAt; }
    public void setSentAt(LocalDateTime sentAt) { this.sentAt = sentAt; }
}
