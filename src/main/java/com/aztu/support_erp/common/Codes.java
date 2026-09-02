package com.aztu.support_erp.common;

import com.aztu.support_erp.common.exception.BadRequestException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/** Validates incoming status/format/type strings against the allowed set of a {@link CodedEnum}. */
public final class Codes {
    private Codes() {}

    public static <E extends Enum<E> & CodedEnum> Set<String> allowed(Class<E> type) {
        return Arrays.stream(type.getEnumConstants()).map(CodedEnum::code).collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    public static <E extends Enum<E> & CodedEnum> boolean isValid(Class<E> type, String value) {
        if (value == null) return false;
        return allowed(type).contains(value);
    }

    /** Validate a value or throw a 400 listing the allowed codes. */
    public static <E extends Enum<E> & CodedEnum> String require(Class<E> type, String value) {
        if (!isValid(type, value)) {
            throw new BadRequestException("Invalid value '" + value + "'. Allowed: " + allowed(type));
        }
        return value;
    }

    public static <E extends Enum<E> & CodedEnum> E from(Class<E> type, String value) {
        for (E e : type.getEnumConstants()) {
            if (e.code().equals(value)) return e;
        }
        throw new BadRequestException("Invalid value '" + value + "'. Allowed: " + allowed(type));
    }
}
