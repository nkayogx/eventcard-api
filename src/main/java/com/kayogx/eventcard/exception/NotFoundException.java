package com.kayogx.eventcard.exception;

/**
 * Throw this when something does not exist - or belongs to another company.
 * The API answers with "404 Not Found".
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
