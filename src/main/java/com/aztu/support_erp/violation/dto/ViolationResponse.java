package com.aztu.support_erp.violation.dto;

import com.aztu.support_erp.user.dto.UserSummary;
import java.time.LocalDateTime;
import java.util.UUID;

/** A row of the violations tab: who, how many, and what has been done about it. */
public record ViolationResponse(
        UUID id,
        UserSummary user,
        int irrelevantCount,
        boolean warned,
        LocalDateTime warnedAt,
        boolean blocked,
        LocalDateTime blockedAt,
        UserSummary blockedBy,
        LocalDateTime unblockedAt,
        UserSummary unblockedBy,
        LocalDateTime updatedAt) {}
