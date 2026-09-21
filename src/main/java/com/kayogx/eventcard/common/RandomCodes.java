package com.kayogx.eventcard.common;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Makes long random codes that nobody can guess,
 * used for invitation links and domain verification.
 */
public final class RandomCodes {

    private static final SecureRandom RANDOM = new SecureRandom();

    private RandomCodes() {
    }

    /** Returns a random, URL-safe code such as "q3F9xT...". */
    public static String newCode() {
        byte[] randomBytes = new byte[24];
        RANDOM.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }
}
