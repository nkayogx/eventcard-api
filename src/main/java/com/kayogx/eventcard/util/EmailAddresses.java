package com.kayogx.eventcard.util;

/** Small helper so that "John@Mail.com " and "john@mail.com" count as the same email. */
public final class EmailAddresses {

    private EmailAddresses() {
    }

    public static String normalize(String email) {
        return email.trim().toLowerCase();
    }
}
