package com.kayogx.eventcard.model;

/** Whether a company may use the platform at all. */
public enum AccountStatus {
    /** Normal - the company can log in and work. */
    ACTIVE,
    /** Blocked by the platform admin - all requests from its users are refused. */
    SUSPENDED
}
