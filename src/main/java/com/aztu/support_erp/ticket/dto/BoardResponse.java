package com.aztu.support_erp.ticket.dto;

import java.util.List;

/**
 * The Kanban board, already grouped. The columns arrive in render order and each one carries the
 * statuses it holds, so a drop target knows what it is asking for without hardcoding the mapping.
 */
public record BoardResponse(List<Column> columns) {

    public record Column(
            String code,
            List<String> statuses,
            /** The default a card dropped here becomes; null means the board must ask. */
            String defaultStatus,
            long total,
            List<TicketResponse> tickets) {}
}
