package com.aztu.support_erp.common.enums;
import com.aztu.support_erp.common.CodedEnum;

/** What an outbox row asks the auth service to do with an account. */
public enum AuthSyncType implements CodedEnum {
    BLOCK("BLOCK"), UNBLOCK("UNBLOCK");
    private final String code; AuthSyncType(String c){this.code=c;}
    public String code(){return code;}

    /** The auth endpoint this event posts to, appended to the configured base URL. */
    public String path(java.util.UUID ssoUserId) {
        return "/auth/internal/users/" + ssoUserId + (this == BLOCK ? "/block" : "/unblock");
    }
}
