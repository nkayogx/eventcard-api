package com.kayogx.eventcard.card;

import com.kayogx.eventcard.company.Company;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Builds a guest's personal link, e.g. https://invites.kayoevents.com/i/Xk9p2QmT7aBc
 *
 * If the company has a verified custom domain we use it; otherwise our own website address.
 */
@Component
public class InvitationLinks {

    private final String websiteAddress;

    public InvitationLinks(@Value("${app.frontend-url}") String websiteAddress) {
        this.websiteAddress = websiteAddress;
    }

    public String linkFor(Company company, String invitationCode) {
        boolean hasOwnDomain = company.getCustomDomain() != null && company.isCustomDomainVerified();
        String site = hasOwnDomain ? "https://" + company.getCustomDomain() : websiteAddress;
        return site + "/i/" + invitationCode;
    }
}
