package com.aztu.support_erp.ticket.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A DEV moving a ticket. {@code comment} is the note kept in the history; {@code cancelReason} is
 * what the reporter is shown and is required whenever the target status is CANCELED.
 *
 * <p>{@code irrelevant} is only meaningful on a cancellation, and setting it is what advances the
 * reporter along the warning → block ladder.
 */
public record ChangeStatusRequest(
        @NotBlank String status,
        @Size(max = 2000) String comment,
        boolean irrelevant,
        @Size(max = 2000) String cancelReason) {}
