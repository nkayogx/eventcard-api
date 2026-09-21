package com.kayogx.eventcard.event;

import java.util.List;

/**
 * Where an event is in its life:
 * DRAFT (being prepared) -> ACTIVE (cards can be sent) -> FINISHED.
 * DRAFT or ACTIVE events can also be CANCELLED.
 */
public enum EventStatus {
    DRAFT,
    ACTIVE,
    FINISHED,
    CANCELLED;

    /** Which statuses this one may change to. FINISHED and CANCELLED are final. */
    public List<EventStatus> allowedNextStatuses() {
        return switch (this) {
            case DRAFT -> List.of(ACTIVE, CANCELLED);
            case ACTIVE -> List.of(FINISHED, CANCELLED);
            case FINISHED, CANCELLED -> List.of();
        };
    }

    /** Finished and cancelled events are kept as a record and can no longer be changed. */
    public boolean isReadOnly() {
        return this == FINISHED || this == CANCELLED;
    }
}
