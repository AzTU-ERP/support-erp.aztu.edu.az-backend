package com.aztu.support_erp.violation.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aztu.support_erp.violation.service.ViolationRules.Outcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** The escalation ladder: nothing, then a warning, then a block — at whatever thresholds are set. */
class ViolationRulesTest {

    private static final int WARN = 1;
    private static final int BLOCK = 2;

    @Nested
    @DisplayName("With the shipped policy (warn at 1, block at 2)")
    class DefaultPolicy {

        @Test
        void aCleanRecordEarnsNothing() {
            assertEquals(Outcome.NONE, ViolationRules.outcomeFor(0, WARN, BLOCK));
        }

        @Test
        void theFirstIrrelevantReportWarns() {
            assertEquals(Outcome.WARNED, ViolationRules.outcomeFor(1, WARN, BLOCK));
        }

        @Test
        void theSecondBlocks() {
            assertEquals(Outcome.BLOCKED, ViolationRules.outcomeFor(2, WARN, BLOCK));
        }

        @Test
        void anythingBeyondTheLimitStaysBlocked() {
            assertEquals(Outcome.BLOCKED, ViolationRules.outcomeFor(7, WARN, BLOCK));
        }
    }

    @Nested
    @DisplayName("With the thresholds moved")
    class ConfiguredPolicy {

        @Test
        void aMoreForgivingPolicyWarnsAndBlocksLater() {
            assertEquals(Outcome.NONE, ViolationRules.outcomeFor(2, 3, 5));
            assertEquals(Outcome.WARNED, ViolationRules.outcomeFor(3, 3, 5));
            assertEquals(Outcome.WARNED, ViolationRules.outcomeFor(4, 3, 5));
            assertEquals(Outcome.BLOCKED, ViolationRules.outcomeFor(5, 3, 5));
        }

        @Test
        void thresholdsThatCoincideBlockWithoutADistinctWarningStep() {
            assertEquals(Outcome.NONE, ViolationRules.outcomeFor(0, 1, 1));
            assertEquals(Outcome.BLOCKED, ViolationRules.outcomeFor(1, 1, 1));
        }

        @Test
        void loweringTheThresholdsUnderAnExistingCountStillBlocks() {
            // The count was earned under a laxer policy; blocking wins over warning regardless.
            assertEquals(Outcome.BLOCKED, ViolationRules.outcomeFor(9, 1, 2));
        }
    }

    @Nested
    @DisplayName("Threshold configuration")
    class Configuration {

        @Test
        void acceptsAWarningBelowTheBlock() {
            assertDoesNotThrow(() -> ViolationRules.requireSaneThresholds(1, 2));
            assertDoesNotThrow(() -> ViolationRules.requireSaneThresholds(3, 3));
        }

        @Test
        void rejectsAWarningThresholdBelowOne() {
            assertThrows(IllegalArgumentException.class,
                    () -> ViolationRules.requireSaneThresholds(0, 2));
        }

        @Test
        void rejectsBlockingBeforeWarning() {
            assertThrows(IllegalArgumentException.class,
                    () -> ViolationRules.requireSaneThresholds(3, 2));
        }

        @Test
        void outcomesRefuseToBeComputedFromAnImpossiblePolicy() {
            assertThrows(IllegalArgumentException.class, () -> ViolationRules.outcomeFor(1, 3, 2));
        }
    }
}
