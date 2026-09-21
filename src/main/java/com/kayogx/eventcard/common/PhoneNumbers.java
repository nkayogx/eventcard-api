package com.kayogx.eventcard.common;

import java.util.Map;
import java.util.Optional;

/**
 * Turns phone numbers, typed in any common way, into one international format
 * that WhatsApp and SMS services understand: "+" followed by digits.
 *
 * For a company in Tanzania (country code "TZ", calling code 255):
 *   "0712 345 678"     becomes  "+255712345678"
 *   "712345678"        becomes  "+255712345678"   (Excel often drops the leading 0)
 *   "255712345678"     becomes  "+255712345678"
 *   "00255712345678"   becomes  "+255712345678"
 *   "+255 712-345-678" becomes  "+255712345678"
 */
public final class PhoneNumbers {

    /** International calling code for each country we support. */
    private static final Map<String, String> CALLING_CODES = Map.ofEntries(
            Map.entry("TZ", "255"), Map.entry("KE", "254"), Map.entry("UG", "256"),
            Map.entry("RW", "250"), Map.entry("BI", "257"), Map.entry("CD", "243"),
            Map.entry("ZM", "260"), Map.entry("MW", "265"), Map.entry("MZ", "258"),
            Map.entry("ZA", "27"), Map.entry("NG", "234"), Map.entry("GH", "233"),
            Map.entry("ET", "251"), Map.entry("AE", "971"), Map.entry("GB", "44"),
            Map.entry("US", "1"));

    private static final String VALID_INTERNATIONAL_NUMBER = "^\\+[0-9]{8,15}$";

    private PhoneNumbers() {
    }

    /**
     * @param typedNumber the number as the user typed it
     * @param countryCode the company's two-letter country, used when the number has no country part
     * @return the cleaned-up number, or empty if it cannot be a real phone number
     */
    public static Optional<String> toInternationalFormat(String typedNumber, String countryCode) {
        if (typedNumber == null || typedNumber.isBlank()) {
            return Optional.empty();
        }
        String trimmed = typedNumber.trim();
        boolean startsWithPlus = trimmed.startsWith("+");
        String digitsOnly = trimmed.replaceAll("[^0-9]", "");
        String callingCode = CALLING_CODES.getOrDefault(countryCode.toUpperCase(), "");

        String international;
        if (startsWithPlus) {
            international = "+" + digitsOnly;
        } else if (digitsOnly.startsWith("00")) {
            international = "+" + digitsOnly.substring(2);
        } else if (digitsOnly.startsWith("0")) {
            international = "+" + callingCode + digitsOnly.substring(1);
        } else if (!callingCode.isEmpty() && digitsOnly.startsWith(callingCode) && digitsOnly.length() > 10) {
            international = "+" + digitsOnly;
        } else {
            international = "+" + callingCode + digitsOnly;
        }

        return international.matches(VALID_INTERNATIONAL_NUMBER) ? Optional.of(international) : Optional.empty();
    }
}
