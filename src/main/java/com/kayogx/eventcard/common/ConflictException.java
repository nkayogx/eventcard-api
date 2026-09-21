package com.kayogx.eventcard.common;

/**
 * Throw this when a request clashes with existing data or a business rule,
 * for example "this email is already registered".
 * The API answers with "409 Conflict".
 */
public class ConflictException extends RuntimeException {

    private final String field;

    public ConflictException(String message) {
        this(message, null);
    }

    /** @param field the form input the problem is about, e.g. "email" */
    public ConflictException(String message, String field) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
