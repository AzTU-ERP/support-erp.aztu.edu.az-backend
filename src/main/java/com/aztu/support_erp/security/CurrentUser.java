package com.aztu.support_erp.security;

import com.aztu.support_erp.common.exception.ApiException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Convenience accessor for the authenticated caller's SSO identity. */
public final class CurrentUser {
    private CurrentUser() {}

    private static final class UnauthorizedException extends ApiException {
        UnauthorizedException() { super(HttpStatus.UNAUTHORIZED, "Authentication is required"); }
    }

    public static SsoUser get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof SsoUser u) {
            return u;
        }
        throw new UnauthorizedException();
    }

    public static UUID ssoId() {
        return get().userId();
    }
}
