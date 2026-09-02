package com.aztu.support_erp.violation.dto;

/**
 * Where the caller stands, for the banner above the ticket form.
 *
 * <p>The thresholds travel with the answer so the warning can say what actually happens next
 * without the frontend keeping its own copy of numbers that live in the service's config.
 */
public record ViolationStatusResponse(
        int irrelevantCount,
        boolean warned,
        boolean blocked,
        int warnThreshold,
        int blockThreshold,
        /** How many more irrelevant reports it would take to block the account. */
        int remainingBeforeBlock) {}
