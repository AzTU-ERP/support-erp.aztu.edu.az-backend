package com.aztu.support_erp.common.exception;

import org.springframework.http.HttpStatus;

/** Raised when the caller is authenticated but the resource belongs to somebody else. */
public class ForbiddenException extends ApiException {
    public ForbiddenException(String message) { super(HttpStatus.FORBIDDEN, message); }
}
