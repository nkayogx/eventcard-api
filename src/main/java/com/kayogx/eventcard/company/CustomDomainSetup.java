package com.kayogx.eventcard.company;

/**
 * The company's own domain and the DNS record the vendor must add to prove they own it.
 *
 * Example: to verify "invites.kayoevents.com" the vendor adds a TXT record
 * named "_eventcard.invites.kayoevents.com" with the value in {@code txtRecordValue}.
 */
public record CustomDomainSetup(
        String domain,
        boolean verified,
        String txtRecordName,
        String txtRecordValue
) {

    /** The prefix of the DNS record name we ask vendors to create. */
    public static final String TXT_RECORD_PREFIX = "_eventcard.";

    /** Returns null when the company has not set a custom domain. */
    public static CustomDomainSetup from(Company company) {
        if (company.getCustomDomain() == null) {
            return null;
        }
        return new CustomDomainSetup(
                company.getCustomDomain(),
                company.isCustomDomainVerified(),
                TXT_RECORD_PREFIX + company.getCustomDomain(),
                company.getDomainVerificationCode());
    }
}
