package com.aztu.support_erp.user.dto;

import java.util.UUID;

/** Just enough of a user to render a row without leaking the rest of their profile. */
public record UserSummary(UUID id, String fullName, String email) {}
