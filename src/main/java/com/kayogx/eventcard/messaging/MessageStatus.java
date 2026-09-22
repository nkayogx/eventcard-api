package com.kayogx.eventcard.messaging;

/**
 * Where a message is on its way to the guest:
 * QUEUED -> SENDING -> SENT -> DELIVERED -> READ, or FAILED.
 */
public enum MessageStatus {
    QUEUED,
    SENDING,
    SENT,
    DELIVERED,
    READ,
    FAILED;

    /** Whether the message is still waiting to go out (so we don't queue it twice). */
    public boolean isOnItsWay() {
        return this == QUEUED || this == SENDING;
    }

    /** Statuses only move forward: a late "delivered" report must never overwrite "read". */
    public boolean isBefore(MessageStatus other) {
        return this.ordinal() < other.ordinal();
    }
}
