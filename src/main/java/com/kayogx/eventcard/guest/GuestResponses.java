package com.kayogx.eventcard.guest;

import java.util.List;
import java.util.UUID;

/** The shapes of guest data the API sends back. */
public final class GuestResponses {

    private GuestResponses() {
    }

    /** One guest (one card), with its card type's name and seats filled in for display. */
    public record GuestDetails(
            UUID id,
            String nameOnCard,
            String phone,
            UUID cardTypeId,
            String cardTypeName,
            int seats,
            String groupName,
            String notes
    ) {
    }

    /**
     * One page of the guest list. {@code groupNames} lists every group used in the event,
     * so the screen can offer them as a filter.
     */
    public record GuestPage(List<GuestDetails> guests, int page, int totalPages, long totalGuests, List<String> groupNames) {
    }
}
