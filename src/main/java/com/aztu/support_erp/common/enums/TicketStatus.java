package com.aztu.support_erp.common.enums;
import com.aztu.support_erp.common.CodedEnum;
import java.util.Set;

/**
 * Lifecycle of a support ticket. Unlike the other modules' codes these are upper case: the
 * support API contract names them that way and they travel unchanged all the way to the board.
 *
 * <p>OPEN -> IN_REVIEW|CANCELED|BLOCKED; IN_REVIEW -> RESOLVED|CANCELED|BLOCKED;
 * BLOCKED (waiting on something outside the DEV's hands) -> IN_REVIEW|CANCELED.
 * RESOLVED and CANCELED are terminal.
 */
public enum TicketStatus implements CodedEnum {
    OPEN("OPEN"), IN_REVIEW("IN_REVIEW"), RESOLVED("RESOLVED"),
    CANCELED("CANCELED"), BLOCKED("BLOCKED");
    private final String code; TicketStatus(String c){this.code=c;}
    public String code(){return code;}

    /** Tickets the DEV queue still owes an answer on. */
    public static final Set<String> OPEN_SET = Set.of(OPEN.code, IN_REVIEW.code, BLOCKED.code);

    /** Settled tickets — nothing may move out of these. */
    public static final Set<String> TERMINAL = Set.of(RESOLVED.code, CANCELED.code);
}
