package com.kayogx.eventcard.company;

import java.time.Instant;
import java.util.UUID;

/** The full company profile, as shown on the company settings page. */
public record CompanyDetails(
        UUID id,
        String name,
        String slug,
        String logoUrl,
        String contactPhone,
        String contactEmail,
        String address,
        String city,
        String countryCode,
        String timeZone,
        String primaryColor,
        String secondaryColor,
        CustomDomainSetup customDomain,
        AccountStatus accountStatus,
        boolean canSendMessages,
        Instant createdAt
) {

    public static CompanyDetails from(Company company) {
        return new CompanyDetails(
                company.getId(),
                company.getName(),
                company.getSlug(),
                company.getLogoUrl(),
                company.getContactPhone(),
                company.getContactEmail(),
                company.getAddress(),
                company.getCity(),
                company.getCountryCode(),
                company.getTimeZone(),
                company.getPrimaryColor(),
                company.getSecondaryColor(),
                CustomDomainSetup.from(company),
                company.getAccountStatus(),
                company.isCanSendMessages(),
                company.getCreatedAt());
    }

    /** The same details, with the CNAME target filled into the custom domain instructions. */
    public CompanyDetails withCnameTarget(String cnameTarget) {
        CustomDomainSetup domainWithTarget = customDomain == null ? null : customDomain.withCnameTarget(cnameTarget);
        return new CompanyDetails(id, name, slug, logoUrl, contactPhone, contactEmail, address, city, countryCode,
                timeZone, primaryColor, secondaryColor, domainWithTarget, accountStatus, canSendMessages, createdAt);
    }
}
