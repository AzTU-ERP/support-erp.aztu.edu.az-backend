package com.aztu.support_erp.user.dto;

import java.util.Set;
import java.util.UUID;

public record UserRefResponse(UUID id, UUID ssoUserId, String fullName, String email, Set<String> roles) {}
