package com.kayogx.eventcard.auth;

import com.kayogx.eventcard.company.Company;
import com.kayogx.eventcard.company.CompanySummary;
import com.kayogx.eventcard.user.User;
import com.kayogx.eventcard.user.UserRole;

import java.util.UUID;

/**
 * "Who am I?" - the logged-in user, their role and their company.
 * The React app uses this to decide which menus to show.
 * {@code company} is empty (null) for platform admins.
 */
public record MeResponse(UUID userId, String fullName, String email, UserRole role, CompanySummary company) {

    public static MeResponse from(User user, Company companyOrNull) {
        CompanySummary company = companyOrNull == null ? null : CompanySummary.from(companyOrNull);
        return new MeResponse(user.getId(), user.getFullName(), user.getEmail(), user.getRole(), company);
    }
}
