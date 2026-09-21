package com.kayogx.eventcard.common;

/**
 * Throw this when someone is trying too many times (e.g. guessing invitation codes).
 * The API answers with "429 Too Many Requests".
 */
public class TooManyRequestsException extends RuntimeException {

    public TooManyRequestsException(String message) {
        super(message);
    }
}
