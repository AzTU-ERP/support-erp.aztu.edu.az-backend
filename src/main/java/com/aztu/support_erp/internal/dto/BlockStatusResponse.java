package com.aztu.support_erp.internal.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * The answer the auth service needs at sign-in: may this account in?
 *
 * <p>{@code reason} is the message to show the person being turned away, worded here so support
 * and auth cannot say two different things about the same block.
 */
public record BlockStatusResponse(
        UUID userId,
        boolean blocked,
        LocalDateTime blockedAt,
        int irrelevantCount,
        String reason) {}
