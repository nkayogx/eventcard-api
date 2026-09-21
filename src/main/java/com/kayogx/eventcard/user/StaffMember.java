package com.kayogx.eventcard.user;

import java.time.Instant;
import java.util.UUID;

/** One row in the company's staff list. */
public record StaffMember(
        UUID id,
        String fullName,
        String email,
        String phone,
        UserRole role,
        boolean active,
        Instant createdAt
) {

    public static StaffMember from(User user) {
        return new StaffMember(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getPhone(),
                user.getRole(),
                user.isActive(),
                user.getCreatedAt());
    }
}
