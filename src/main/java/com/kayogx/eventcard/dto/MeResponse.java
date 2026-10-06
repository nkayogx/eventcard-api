package com.kayogx.eventcard.dto;

import com.kayogx.eventcard.model.Company;
import com.kayogx.eventcard.model.User;
import com.kayogx.eventcard.model.UserRole;

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
