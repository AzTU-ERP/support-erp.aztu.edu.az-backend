package com.aztu.support_erp.ticket.dto;

import com.aztu.support_erp.user.dto.UserSummary;
import java.time.LocalDateTime;
import java.util.UUID;

/** One line of the audit trail. {@code fromStatus} is null on the entry that opened the ticket. */
public record TicketHistoryResponse(
        UUID id,
        String fromStatus,
        String toStatus,
        UserSummary changedBy,
        String comment,
        LocalDateTime createdAt) {}
