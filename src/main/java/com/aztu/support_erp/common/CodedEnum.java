package com.aztu.support_erp.common;

/** Enums whose persisted form is a stable lowercase string code (matches the schema's varchar values). */
public interface CodedEnum {
    String code();
}
