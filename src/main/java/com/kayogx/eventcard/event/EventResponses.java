package com.kayogx.eventcard.event;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * The shapes of event data the API sends back.
 * Kept together in one file because they are small and belong together.
 */
public final class EventResponses {

    private EventResponses() {
    }

    /** One row in the events list. */
    public record EventSummary(
            UUID id,
            String name,
            EventType eventType,
            LocalDateTime startsAt,
            String venueName,
            EventStatus status,
            long totalCards,
            long totalSeats
    ) {
    }

    public record EventPage(List<EventSummary> events, int page, int totalPages, long totalEvents) {
    }

    /** A card type, with how many cards and seats of this type are on the guest list. */
    public record CardTypeDetails(UUID id, String name, int seats, long cards, long seatsUsed) {
    }

    /** Totals for one guest group, e.g. "Bride's side". groupName is null for guests without a group. */
    public record GroupTotals(String groupName, long cards, long seats) {
    }

    /** Everything about one event, as shown on the event page. */
    public record EventDetails(
            UUID id,
            String name,
            EventType eventType,
            String hostNames,
            LocalDateTime startsAt,
            LocalDateTime endsAt,
            String timeZone,
            String venueName,
            String venueAddress,
            String mapLink,
            String dressCode,
            String extraInfo,
            String contactPhone,
            LocalDate rsvpDeadline,
            EventStatus status,
            List<EventStatus> allowedNextStatuses,
            List<CardTypeDetails> cardTypes,
            List<GroupTotals> groups,
            long totalCards,
            long totalSeats
    ) {
    }
}
