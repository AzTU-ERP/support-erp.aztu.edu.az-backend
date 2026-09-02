package com.aztu.support_erp.security;

import com.aztu.support_erp.common.enums.SupportRole;
import java.util.Set;
import java.util.UUID;

/** The authenticated caller as described by the central SSO microservice. No passwords are ever stored. */
public record SsoUser(UUID userId, String fullName, String email, Set<String> roles) {

    public boolean hasRole(SupportRole role) {
        return roles.contains(role.code());
    }

    /** DEVs own the queue: everything beyond opening and reading one's own tickets. */
    public boolean isDev() {
        return hasRole(SupportRole.DEV);
    }
}
