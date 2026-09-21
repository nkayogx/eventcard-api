package com.kayogx.eventcard.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PhoneNumbersTest {

    @Test
    void commonWaysOfTypingATanzanianNumberAllGiveTheSameResult() {
        String expected = "+255712345678";

        assertThat(PhoneNumbers.toInternationalFormat("0712 345 678", "TZ")).contains(expected);
        assertThat(PhoneNumbers.toInternationalFormat("712345678", "TZ")).contains(expected);
        assertThat(PhoneNumbers.toInternationalFormat("255712345678", "TZ")).contains(expected);
        assertThat(PhoneNumbers.toInternationalFormat("+255 712-345-678", "TZ")).contains(expected);
        assertThat(PhoneNumbers.toInternationalFormat("00255712345678", "TZ")).contains(expected);
    }

    @Test
    void theCompanysCountryDecidesTheCountryPart() {
        assertThat(PhoneNumbers.toInternationalFormat("0712345678", "KE")).contains("+254712345678");
    }

    @Test
    void nonsenseIsNotAPhoneNumber() {
        assertThat(PhoneNumbers.toInternationalFormat("hello", "TZ")).isEmpty();
        assertThat(PhoneNumbers.toInternationalFormat("12", "TZ")).isEmpty();
        assertThat(PhoneNumbers.toInternationalFormat("", "TZ")).isEmpty();
    }
}
