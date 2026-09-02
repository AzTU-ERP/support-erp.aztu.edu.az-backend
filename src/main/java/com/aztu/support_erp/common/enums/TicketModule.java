package com.aztu.support_erp.common.enums;
import com.aztu.support_erp.common.CodedEnum;

/**
 * The modules a ticket can be filed against — the same closed set the {@code support_modules}
 * CHECK constraint enforces.
 *
 * <p>Which modules and sections are actually <em>offered</em> is a question for the catalogue in
 * the database, not for this enum: an admin may deactivate or rename any of them without a
 * deployment. This exists so a typo cannot reach the catalogue lookup in the first place.
 */
public enum TicketModule implements CodedEnum {
    LMS("LMS"), HR("HR"), LIBRARY("LIBRARY"),
    FINANCE("FINANCE"), EXAM("EXAM"), TURNIKET("TURNIKET");
    private final String code; TicketModule(String c){this.code=c;}
    public String code(){return code;}
}
