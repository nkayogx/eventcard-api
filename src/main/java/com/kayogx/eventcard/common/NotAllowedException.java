package com.kayogx.eventcard.common;

/**
 * Throw this when the user is logged in but is not allowed to do this action.
 * The API answers with "403 Forbidden".
 */
public class NotAllowedException extends RuntimeException {

    public NotAllowedException(String message) {
        super(message);
    }
}
