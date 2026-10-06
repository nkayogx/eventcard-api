package com.kayogx.eventcard.exception;

/**
 * Throw this when a login attempt fails (wrong password, deactivated account...).
 * The API answers with "401 Unauthorized".
 */
public class LoginFailedException extends RuntimeException {

    public LoginFailedException(String message) {
        super(message);
    }
}
