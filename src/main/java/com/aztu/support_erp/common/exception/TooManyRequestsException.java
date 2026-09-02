package com.aztu.support_erp.common.exception;

import org.springframework.http.HttpStatus;

/** Raised when a reporter trips the per-user ticket rate limit. */
public class TooManyRequestsException extends ApiException {
    public TooManyRequestsException(String message) { super(HttpStatus.TOO_MANY_REQUESTS, message); }
}
