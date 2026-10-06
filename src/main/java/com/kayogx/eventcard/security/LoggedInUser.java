package com.kayogx.eventcard.security;

import com.kayogx.eventcard.exception.NotAllowedException;
import com.kayogx.eventcard.model.UserRole;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

/**
 * Who is making the current request. Filled in by {@link JwtAuthFilter}.
 *
 * Anywhere in the code you can ask:  LoggedInUser.current().companyId()
 */
public record LoggedInUser(UUID userId, UUID companyId, UserRole role, String email) {

    public static LoggedInUser current() {
        Authentication login = SecurityContextHolder.getContext().getAuthentication();
        if (login == null || !(login.getPrincipal() instanceof LoggedInUser loggedInUser)) {
            throw new NotAllowedException("You must be logged in to do this.");
        }
        return loggedInUser;
    }
}
