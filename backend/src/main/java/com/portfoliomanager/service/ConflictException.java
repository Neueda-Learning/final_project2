package com.portfoliomanager.service;

/**
 * Business conflict converted to HTTP 409 by {@code ApiExceptionHandler}.
 * The message is the client-facing error code, for example:
 * {@code PORTFOLIO_NAME_CONFLICT} for a duplicate active name or
 * {@code PORTFOLIO_HAS_TRADES} when transaction history prevents hard deletion.
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String code) {
        super(code);
    }
}
