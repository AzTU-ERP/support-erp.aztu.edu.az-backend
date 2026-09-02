package com.aztu.support_erp.ticket.dto;

import java.util.UUID;

/**
 * Who owns the ticket. A null {@code devId} hands it back to the queue; passing one's own id is
 * the "take it" button on the board.
 */
public record AssignRequest(UUID devId) {}
