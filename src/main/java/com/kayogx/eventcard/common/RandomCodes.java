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

    private static final String LETTERS_AND_DIGITS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";

    /**
     * A short code for a guest's personal link, e.g. "Xk9p2QmT7aBc".
     * 12 characters from 56 letters/digits (look-alikes such as 0/O and 1/l are left out)
     * gives about 70 bits of randomness - far too many to guess.
     */
    public static String newInvitationCode() {
        StringBuilder code = new StringBuilder();
        for (int position = 0; position < 12; position++) {
            code.append(LETTERS_AND_DIGITS.charAt(RANDOM.nextInt(LETTERS_AND_DIGITS.length())));
        }
        return code.toString();
    }

    /** Returns a random, URL-safe code such as "q3F9xT...". */
    public static String newCode() {
        byte[] randomBytes = new byte[24];
        RANDOM.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }
}
