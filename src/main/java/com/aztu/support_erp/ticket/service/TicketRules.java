package com.aztu.support_erp.ticket.service;

import com.aztu.support_erp.common.enums.BoardColumn;
import com.aztu.support_erp.common.enums.TicketStatus;
import com.aztu.support_erp.common.exception.BadRequestException;
import com.aztu.support_erp.common.exception.ConflictException;
import java.util.Map;
import java.util.Set;

/**
 * The ticket lifecycle's rules, kept free of Spring and JPA so they can be exercised directly.
 * Everything here is enforced server-side; the board's equivalent checks are UX only.
 */
public final class TicketRules {

    /** Which statuses each status may move to. Anything not listed is rejected. */
    private static final Map<String, Set<String>> TRANSITIONS = Map.of(
            TicketStatus.OPEN.code(),      Set.of(TicketStatus.IN_REVIEW.code(), TicketStatus.CANCELED.code(),
                                                  TicketStatus.BLOCKED.code()),
            TicketStatus.IN_REVIEW.code(), Set.of(TicketStatus.RESOLVED.code(), TicketStatus.CANCELED.code(),
                                                  TicketStatus.BLOCKED.code()),
            TicketStatus.BLOCKED.code(),   Set.of(TicketStatus.IN_REVIEW.code(), TicketStatus.CANCELED.code()),
            TicketStatus.RESOLVED.code(),  Set.of(),
            TicketStatus.CANCELED.code(),  Set.of());

    private TicketRules() {}

    /** A move the board offers but the lifecycle forbids is a 409, not a 400: the ticket moved on. */
    public static void requireTransition(String from, String to) {
        if (!TRANSITIONS.getOrDefault(from, Set.of()).contains(to)) {
            throw new ConflictException("A '" + from + "' ticket cannot become '" + to + "'");
        }
    }

    /** Whether the move is legal, for callers that want to offer the choice rather than take it. */
    public static boolean canTransition(String from, String to) {
        return TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }

    /**
     * Where a card dropped into a column lands when the column holds more than one status.
     *
     * <p>IN PROGRESS means "a DEV is on it", so IN_REVIEW. DONE is genuinely ambiguous — resolved
     * and cancelled are different outcomes with different consequences for the reporter — so the
     * board must ask, and this returns null to say so.
     */
    public static String defaultStatusFor(BoardColumn column) {
        return switch (column) {
            case TO_DO -> TicketStatus.OPEN.code();
            case IN_PROGRESS -> TicketStatus.IN_REVIEW.code();
            case DONE -> null;
        };
    }

    /**
     * A cancellation always states why: it is the only thing the reporter is told, and when the
     * ticket is also marked irrelevant it is the evidence behind a warning or a block.
     */
    public static void validateCancellation(String targetStatus, boolean irrelevant, String reason) {
        boolean cancelling = TicketStatus.CANCELED.code().equals(targetStatus);
        if (cancelling && (reason == null || reason.isBlank())) {
            throw new BadRequestException("A reason is required when cancelling a ticket");
        }
        if (irrelevant && !cancelling) {
            throw new BadRequestException("A ticket can only be marked irrelevant while being cancelled");
        }
    }

    /** True once the ticket has reached an outcome and nothing more will happen to it. */
    public static boolean isTerminal(String status) {
        return TicketStatus.TERMINAL.contains(status);
    }
}
