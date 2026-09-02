package com.aztu.support_erp.common.enums;
import com.aztu.support_erp.common.CodedEnum;

/**
 * Roles issued by the central SSO that mean something here. The code is the exact string the auth
 * service puts in the token's {@code roles} claim; Spring Security sees it prefixed with
 * {@code ROLE_}.
 *
 * <p>Everyone else — every authenticated account, whatever their module roles — may open a ticket
 * and read their own. Only {@code dev} sees the queue.
 */
public enum SupportRole implements CodedEnum {
    DEV("dev");
    private final String code; SupportRole(String c){this.code=c;}
    public String code(){return code;}
}
