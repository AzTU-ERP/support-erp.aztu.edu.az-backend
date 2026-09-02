package com.aztu.support_erp.ticket.dto;

import java.util.UUID;

/**
 * A screenshot as the client sees it. {@code url} is a path on this service, not a public link:
 * the bytes are only served to the reporter or a DEV, so an {@code <img src>} cannot fetch it and
 * the frontend loads it through the authenticated client instead.
 */
public record TicketAttachmentResponse(
        UUID id,
        String originalName,
        String mimeType,
        long sizeBytes,
        String url) {}
