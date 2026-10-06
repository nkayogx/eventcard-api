package com.kayogx.eventcard.model;

/** What a person is allowed to do. Each user has exactly one role. */
public enum UserRole {
    /** Runs the company: profile, staff, billing, and everything a manager can do. */
    OWNER,
    /** Creates and edits events and guests, and sends cards. */
    MANAGER,
    /** Can only scan guests at the door. */
    CHECK_IN_STAFF,
    /** The SaaS owner (you). Manages all companies. Does not belong to a company. */
    PLATFORM_ADMIN
}
