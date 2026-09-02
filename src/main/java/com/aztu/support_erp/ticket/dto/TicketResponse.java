package com.aztu.support_erp.ticket.dto;

import com.aztu.support_erp.user.dto.UserSummary;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * One ticket. The same shape serves the reporter's list, the board's cards and the detail drawer;
 * {@code attachments} and {@code history} are only filled in for the detail view and are dropped
 * from the JSON when absent (Jackson is configured non-null).
 */
public record TicketResponse(
        UUID id,
        /** SUP-000123 — what the reporter is told on submit and what a DEV searches by. */
        String reference,
        UserSummary user,
        String module,
        String section,
        String description,
        String status,
        /** Which board column this status falls in, so the frontend never re-derives the mapping. */
        String boardColumn,
        boolean irrelevant,
        String cancelReason,
        UserSummary assignedDev,
        String pageUrl,
        String userAgent,
        int attachmentCount,
        List<TicketAttachmentResponse> attachments,
        List<TicketHistoryResponse> history,
        LocalDateTime resolvedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {}
