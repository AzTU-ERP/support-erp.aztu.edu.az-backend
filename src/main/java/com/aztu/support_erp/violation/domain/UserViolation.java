package com.aztu.support_erp.violation.domain;

import com.aztu.support_erp.common.BaseEntity;
import com.aztu.support_erp.user.domain.UserRef;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * user_violations — the ledger behind a warning or a block. One row per user, created the first
 * time one of their tickets is cancelled as irrelevant.
 *
 * <p>This service decides; the auth service enforces at sign-in. {@code blockedAt} being set is
 * what makes the account blocked here, and an outbox event carries that decision across.
 */
@Entity
@Table(name = "user_violations")
public class UserViolation extends BaseEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private UserRef user;

    @Column(name = "irrelevant_count", nullable = false)
    private int irrelevantCount = 0;

    @Column(name = "warned_at")
    private LocalDateTime warnedAt;

    @Column(name = "blocked_at")
    private LocalDateTime blockedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "blocked_by_dev_id")
    private UserRef blockedByDev;

    @Column(name = "unblocked_at")
    private LocalDateTime unblockedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unblocked_by_dev_id")
    private UserRef unblockedByDev;

    /** Blocked right now — an earlier block that a DEV has since lifted does not count. */
    public boolean isBlocked() {
        return blockedAt != null;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UserRef getUser() { return user; }
    public void setUser(UserRef user) { this.user = user; }
    public int getIrrelevantCount() { return irrelevantCount; }
    public void setIrrelevantCount(int irrelevantCount) { this.irrelevantCount = irrelevantCount; }
    public LocalDateTime getWarnedAt() { return warnedAt; }
    public void setWarnedAt(LocalDateTime warnedAt) { this.warnedAt = warnedAt; }
    public LocalDateTime getBlockedAt() { return blockedAt; }
    public void setBlockedAt(LocalDateTime blockedAt) { this.blockedAt = blockedAt; }
    public UserRef getBlockedByDev() { return blockedByDev; }
    public void setBlockedByDev(UserRef blockedByDev) { this.blockedByDev = blockedByDev; }
    public LocalDateTime getUnblockedAt() { return unblockedAt; }
    public void setUnblockedAt(LocalDateTime unblockedAt) { this.unblockedAt = unblockedAt; }
    public UserRef getUnblockedByDev() { return unblockedByDev; }
    public void setUnblockedByDev(UserRef unblockedByDev) { this.unblockedByDev = unblockedByDev; }
}
