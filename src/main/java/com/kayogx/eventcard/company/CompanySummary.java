package com.kayogx.eventcard.company;

import java.util.UUID;

/** The short version of a company, included in "who am I?" answers. */
public record CompanySummary(
        UUID id,
        String name,
        String slug,
        String logoUrl,
        String primaryColor,
        String secondaryColor,
        AccountStatus accountStatus,
        boolean canSendMessages
) {

    public static CompanySummary from(Company company) {
        return new CompanySummary(
                company.getId(),
                company.getName(),
                company.getSlug(),
                company.getLogoUrl(),
                company.getPrimaryColor(),
                company.getSecondaryColor(),
                company.getAccountStatus(),
                company.isCanSendMessages());
    }
}
