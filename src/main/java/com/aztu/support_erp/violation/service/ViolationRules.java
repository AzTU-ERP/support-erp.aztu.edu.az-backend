package com.aztu.support_erp.violation.service;

/**
 * What a given number of irrelevant reports earns. Free of Spring and JPA so the escalation can
 * be exercised directly, and thresholds are always passed in — nothing here assumes 1 and 2.
 */
public final class ViolationRules {

    /** The escalation ladder. Each step is reached once the count meets its threshold. */
    public enum Outcome {
        /** Below the warning threshold — nothing is said to the reporter. */
        NONE,
        /** Warned: the next irrelevant report blocks the account. */
        WARNED,
        /** Blocked: sign-in is refused until a DEV lifts it. */
        BLOCKED
    }

    private ViolationRules() {}

    /**
     * The outcome for a count, given the configured thresholds.
     *
     * <p>Blocking wins over warning: a count that has passed both means the account is blocked,
     * whether it climbed one step at a time or the thresholds were lowered underneath it.
     */
    public static Outcome outcomeFor(int irrelevantCount, int warnThreshold, int blockThreshold) {
        requireSaneThresholds(warnThreshold, blockThreshold);
        if (irrelevantCount >= blockThreshold) return Outcome.BLOCKED;
        if (irrelevantCount >= warnThreshold) return Outcome.WARNED;
        return Outcome.NONE;
    }

    /**
     * Misconfiguration is a startup-time problem, not a runtime one: a block threshold below the
     * warning threshold would block accounts that were never warned.
     */
    public static void requireSaneThresholds(int warnThreshold, int blockThreshold) {
        if (warnThreshold < 1) {
            throw new IllegalArgumentException("The warning threshold must be at least 1");
        }
        if (blockThreshold < warnThreshold) {
            throw new IllegalArgumentException(
                    "The block threshold (" + blockThreshold + ") cannot be below the warning threshold ("
                            + warnThreshold + ")");
        }
    }
}
