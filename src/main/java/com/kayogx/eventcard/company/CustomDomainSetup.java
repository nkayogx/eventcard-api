package com.kayogx.eventcard.company;

/**
 * The company's own domain and the DNS records the vendor must add.
 *
 * Example for "invites.kayoevents.com":
 *  1. a TXT record named "_eventcard.invites.kayoevents.com" with the value in {@code txtRecordValue}
 *     - proves they own the domain;
 *  2. a CNAME record "invites.kayoevents.com" pointing to {@code cnameTarget}
 *     - sends guests who open the link to our website.
 */
public record CustomDomainSetup(
        String domain,
        boolean verified,
        String txtRecordName,
        String txtRecordValue,
        String cnameTarget
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
                company.getDomainVerificationCode(),
                null);
    }

    public CustomDomainSetup withCnameTarget(String target) {
        return new CustomDomainSetup(domain, verified, txtRecordName, txtRecordValue, target);
    }
}
