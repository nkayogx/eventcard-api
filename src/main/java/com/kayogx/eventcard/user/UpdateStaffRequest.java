package com.kayogx.eventcard.user;

/**
 * Change a staff member's role and/or switch their account on or off.
 * Leave a value out (null) to keep it as it is.
 */
public record UpdateStaffRequest(UserRole role, Boolean active) {
}
