package com.kayogx.eventcard.common;

/**
 * Throw this when the user sent something we cannot accept,
 * for example a logo that is not an image.
 * The API answers with "400 Bad Request".
 */
public class InvalidInputException extends RuntimeException {

    private final String field;

    public InvalidInputException(String message, String field) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
