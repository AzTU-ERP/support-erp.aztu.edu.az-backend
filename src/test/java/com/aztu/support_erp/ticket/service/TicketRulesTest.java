package com.aztu.support_erp.ticket.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aztu.support_erp.common.enums.BoardColumn;
import com.aztu.support_erp.common.enums.TicketStatus;
import com.aztu.support_erp.common.exception.BadRequestException;
import com.aztu.support_erp.common.exception.ConflictException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** The ticket lifecycle: allowed transitions, cancellation rules and the board mapping. */
class TicketRulesTest {

    private static final String OPEN = TicketStatus.OPEN.code();
    private static final String IN_REVIEW = TicketStatus.IN_REVIEW.code();
    private static final String RESOLVED = TicketStatus.RESOLVED.code();
    private static final String CANCELED = TicketStatus.CANCELED.code();
    private static final String BLOCKED = TicketStatus.BLOCKED.code();

    @Nested
    @DisplayName("Lifecycle transitions")
    class Transitions {

        @Test
        void anOpenTicketMayBePickedUpCancelledOrParked() {
            assertDoesNotThrow(() -> TicketRules.requireTransition(OPEN, IN_REVIEW));
            assertDoesNotThrow(() -> TicketRules.requireTransition(OPEN, CANCELED));
            assertDoesNotThrow(() -> TicketRules.requireTransition(OPEN, BLOCKED));
        }

        @Test
        void anOpenTicketCannotJumpStraightToResolved() {
            assertThrows(ConflictException.class, () -> TicketRules.requireTransition(OPEN, RESOLVED));
        }

        @Test
        void aTicketInReviewMayBeResolvedCancelledOrParked() {
            assertDoesNotThrow(() -> TicketRules.requireTransition(IN_REVIEW, RESOLVED));
            assertDoesNotThrow(() -> TicketRules.requireTransition(IN_REVIEW, CANCELED));
            assertDoesNotThrow(() -> TicketRules.requireTransition(IN_REVIEW, BLOCKED));
        }

        @Test
        void aParkedTicketComesBackThroughReview() {
            assertDoesNotThrow(() -> TicketRules.requireTransition(BLOCKED, IN_REVIEW));
            assertDoesNotThrow(() -> TicketRules.requireTransition(BLOCKED, CANCELED));
            // Resolving straight from BLOCKED would skip the review that unblocked it.
            assertThrows(ConflictException.class, () -> TicketRules.requireTransition(BLOCKED, RESOLVED));
            assertThrows(ConflictException.class, () -> TicketRules.requireTransition(BLOCKED, OPEN));
        }

        @Test
        void terminalStatusesGoNowhere() {
            assertThrows(ConflictException.class, () -> TicketRules.requireTransition(RESOLVED, IN_REVIEW));
            assertThrows(ConflictException.class, () -> TicketRules.requireTransition(RESOLVED, CANCELED));
            assertThrows(ConflictException.class, () -> TicketRules.requireTransition(CANCELED, IN_REVIEW));
            assertThrows(ConflictException.class, () -> TicketRules.requireTransition(CANCELED, OPEN));
        }

        @Test
        void aTicketCannotBeMovedToItsOwnStatus() {
            assertThrows(ConflictException.class, () -> TicketRules.requireTransition(OPEN, OPEN));
            assertThrows(ConflictException.class, () -> TicketRules.requireTransition(IN_REVIEW, IN_REVIEW));
        }

        @Test
        void anUnknownStatusIsRejectedRatherThanAssumed() {
            assertThrows(ConflictException.class, () -> TicketRules.requireTransition("MYSTERY", IN_REVIEW));
            assertThrows(ConflictException.class, () -> TicketRules.requireTransition(OPEN, "MYSTERY"));
        }

        @Test
        void canTransitionAnswersWithoutThrowing() {
            assertTrue(TicketRules.canTransition(OPEN, IN_REVIEW));
            assertFalse(TicketRules.canTransition(OPEN, RESOLVED));
        }

        @Test
        void terminalIsExactlyResolvedAndCancelled() {
            assertTrue(TicketRules.isTerminal(RESOLVED));
            assertTrue(TicketRules.isTerminal(CANCELED));
            assertFalse(TicketRules.isTerminal(OPEN));
            assertFalse(TicketRules.isTerminal(IN_REVIEW));
            assertFalse(TicketRules.isTerminal(BLOCKED));
        }
    }

    @Nested
    @DisplayName("Cancelling")
    class Cancelling {

        @Test
        void requiresAReason() {
            assertThrows(BadRequestException.class,
                    () -> TicketRules.validateCancellation(CANCELED, false, null));
            assertThrows(BadRequestException.class,
                    () -> TicketRules.validateCancellation(CANCELED, false, "   "));
        }

        @Test
        void acceptsAStatedReason() {
            assertDoesNotThrow(() -> TicketRules.validateCancellation(CANCELED, false, "Duplicate report"));
        }

        @Test
        void markingItIrrelevantStillNeedsTheReason() {
            assertThrows(BadRequestException.class,
                    () -> TicketRules.validateCancellation(CANCELED, true, null));
            assertDoesNotThrow(() -> TicketRules.validateCancellation(CANCELED, true, "Nothing was broken"));
        }

        @Test
        void irrelevantCannotBeAttachedToAnyOtherMove() {
            assertThrows(BadRequestException.class,
                    () -> TicketRules.validateCancellation(RESOLVED, true, "Nothing was broken"));
            assertThrows(BadRequestException.class,
                    () -> TicketRules.validateCancellation(IN_REVIEW, true, "Nothing was broken"));
        }

        @Test
        void otherMovesNeedNoReason() {
            assertDoesNotThrow(() -> TicketRules.validateCancellation(IN_REVIEW, false, null));
            assertDoesNotThrow(() -> TicketRules.validateCancellation(RESOLVED, false, null));
        }
    }

    @Nested
    @DisplayName("The description")
    class Description {

        @Test
        void isTrimmedBeforeItIsMeasured() {
            assertEquals("Davamiyyet", TicketRules.requireDescription("   Davamiyyet   "));
        }

        @Test
        void acceptsTheShortestAndLongestAllowed() {
            assertDoesNotThrow(() -> TicketRules.requireDescription("0123456789"));
            assertDoesNotThrow(() -> TicketRules.requireDescription("x".repeat(2000)));
        }

        @Test
        void rejectsOneCharacterShortOrLong() {
            assertThrows(BadRequestException.class, () -> TicketRules.requireDescription("012345678"));
            assertThrows(BadRequestException.class, () -> TicketRules.requireDescription("x".repeat(2001)));
        }

        @Test
        void rejectsWhatIsOnlyLongEnoughBeforeTrimming() {
            // Ten characters as sent, nine once trimmed — the value that would reach the column.
            assertThrows(BadRequestException.class, () -> TicketRules.requireDescription(" 123456789"));
        }

        @Test
        void countsCharactersTheWayTheSchemaDoes() {
            // Five emoji are ten UTF-16 units but five characters. String.length() would have let
            // this through, and the CHECK constraint would then have rejected it as a bare 409.
            String fiveEmoji = "😀".repeat(5);
            assertEquals(10, fiveEmoji.length());
            assertThrows(BadRequestException.class, () -> TicketRules.requireDescription(fiveEmoji));

            // Ten of them really are ten characters, and are accepted.
            assertDoesNotThrow(() -> TicketRules.requireDescription("😀".repeat(10)));
        }

        @Test
        void rejectsNothingAtAll() {
            assertThrows(BadRequestException.class, () -> TicketRules.requireDescription(null));
            assertThrows(BadRequestException.class, () -> TicketRules.requireDescription("        "));
        }

        @Test
        void saysHowLongItActuallyWas() {
            BadRequestException ex = assertThrows(BadRequestException.class,
                    () -> TicketRules.requireDescription("qisa"));
            assertTrue(ex.getMessage().contains("4"), ex.getMessage());
        }
    }

    @Nested
    @DisplayName("Board columns")
    class Columns {

        @Test
        void eachStatusBelongsToExactlyOneColumn() {
            assertEquals(BoardColumn.TO_DO, BoardColumn.of(OPEN));
            assertEquals(BoardColumn.IN_PROGRESS, BoardColumn.of(IN_REVIEW));
            assertEquals(BoardColumn.IN_PROGRESS, BoardColumn.of(BLOCKED));
            assertEquals(BoardColumn.DONE, BoardColumn.of(RESOLVED));
            assertEquals(BoardColumn.DONE, BoardColumn.of(CANCELED));
        }

        @Test
        void anUnknownStatusHasNoColumn() {
            assertThrows(IllegalArgumentException.class, () -> BoardColumn.of("MYSTERY"));
        }

        @Test
        void droppingIntoInProgressMeansSomebodyIsOnIt() {
            assertEquals(IN_REVIEW, TicketRules.defaultStatusFor(BoardColumn.IN_PROGRESS));
        }

        @Test
        void droppingIntoDoneIsAmbiguousSoTheBoardMustAsk() {
            assertNull(TicketRules.defaultStatusFor(BoardColumn.DONE));
        }

        @Test
        void theColumnsRenderLeftToRight() {
            assertEquals(
                    java.util.List.of(BoardColumn.TO_DO, BoardColumn.IN_PROGRESS, BoardColumn.DONE),
                    BoardColumn.ordered());
        }
    }
}
