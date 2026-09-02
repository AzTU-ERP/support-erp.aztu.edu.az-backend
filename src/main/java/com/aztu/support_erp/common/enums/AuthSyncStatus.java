package com.aztu.support_erp.common.enums;
import com.aztu.support_erp.common.CodedEnum;

/** Delivery state of an outbox row. Lower case, matching hr-erp's integration_events. */
public enum AuthSyncStatus implements CodedEnum {
    PENDING("pending"), SENT("sent"), FAILED("failed");
    private final String code; AuthSyncStatus(String c){this.code=c;}
    public String code(){return code;}
}
