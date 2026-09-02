package com.aztu.support_erp.user.dto;

import com.aztu.support_erp.user.domain.UserRef;
import java.util.Set;

public final class UserMapper {
    private UserMapper() {}

    public static UserRefResponse toResponse(UserRef u) {
        return new UserRefResponse(u.getId(), u.getSsoUserId(), u.getFullName(), u.getEmail(),
                Set.copyOf(u.getRoles()));
    }

    /** Compact form embedded in ticket payloads — no roles, no SSO id. */
    public static UserSummary toSummary(UserRef u) {
        return u == null ? null : new UserSummary(u.getId(), u.getFullName(), u.getEmail());
    }
}
