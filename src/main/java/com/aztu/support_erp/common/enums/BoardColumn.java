package com.aztu.support_erp.common.enums;
import com.aztu.support_erp.common.CodedEnum;
import java.util.List;
import java.util.Set;

/**
 * The Kanban board's three columns and the statuses each one holds.
 *
 * <p>A column is a view of the statuses, not a status itself — which is why dropping a card into
 * IN_PROGRESS or DONE needs a target status to be chosen (see {@code TicketRules.defaultStatusFor}).
 */
public enum BoardColumn implements CodedEnum {
    TO_DO("TO_DO", Set.of(TicketStatus.OPEN.code())),
    IN_PROGRESS("IN_PROGRESS", Set.of(TicketStatus.IN_REVIEW.code(), TicketStatus.BLOCKED.code())),
    DONE("DONE", Set.of(TicketStatus.RESOLVED.code(), TicketStatus.CANCELED.code()));

    private final String code;
    private final Set<String> statuses;

    BoardColumn(String code, Set<String> statuses) {
        this.code = code;
        this.statuses = statuses;
    }

    public String code() { return code; }

    public Set<String> statuses() { return statuses; }

    /** Left-to-right, the order the board renders them in. */
    public static List<BoardColumn> ordered() {
        return List.of(TO_DO, IN_PROGRESS, DONE);
    }

    /** Which column a ticket in this status belongs to. */
    public static BoardColumn of(String status) {
        for (BoardColumn column : values()) {
            if (column.statuses.contains(status)) return column;
        }
        throw new IllegalArgumentException("No board column holds status '" + status + "'");
    }
}
