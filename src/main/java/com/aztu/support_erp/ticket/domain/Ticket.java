package com.aztu.support_erp.ticket.domain;

import com.aztu.support_erp.common.BaseEntity;
import com.aztu.support_erp.common.enums.TicketStatus;
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
 * tickets — one problem report.
 *
 * <p>The reporter owns the content and nothing else: only a DEV moves the status, and the row
 * records who did it and why. {@code isIrrelevant} is what feeds the violation ledger, and the
 * schema only lets it be true on a cancelled ticket.
 */
@Entity
@Table(name = "tickets")
public class Ticket extends BaseEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** Human-facing number (SUP-000123), handed out by a sequence and shown on submit. */
    @Column(name = "reference", updatable = false, nullable = false, unique = true)
    private String reference;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserRef user;

    @Column(name = "module", nullable = false)
    private String module;

    @Column(name = "section", nullable = false)
    private String section;

    @Column(name = "description", nullable = false, columnDefinition = "text")
    private String description;

    @Column(name = "status", nullable = false)
    private String status = TicketStatus.OPEN.code();

    @Column(name = "is_irrelevant", nullable = false)
    private boolean irrelevant = false;

    @Column(name = "cancel_reason", columnDefinition = "text")
    private String cancelReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_dev_id")
    private UserRef assignedDev;

    /** Captured by the browser, not typed: where the reporter was and what they were using. */
    @Column(name = "page_url")
    private String pageUrl;

    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }
    public UserRef getUser() { return user; }
    public void setUser(UserRef user) { this.user = user; }
    public String getModule() { return module; }
    public void setModule(String module) { this.module = module; }
    public String getSection() { return section; }
    public void setSection(String section) { this.section = section; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public boolean isIrrelevant() { return irrelevant; }
    public void setIrrelevant(boolean irrelevant) { this.irrelevant = irrelevant; }
    public String getCancelReason() { return cancelReason; }
    public void setCancelReason(String cancelReason) { this.cancelReason = cancelReason; }
    public UserRef getAssignedDev() { return assignedDev; }
    public void setAssignedDev(UserRef assignedDev) { this.assignedDev = assignedDev; }
    public String getPageUrl() { return pageUrl; }
    public void setPageUrl(String pageUrl) { this.pageUrl = pageUrl; }
    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
    public LocalDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(LocalDateTime resolvedAt) { this.resolvedAt = resolvedAt; }
}
