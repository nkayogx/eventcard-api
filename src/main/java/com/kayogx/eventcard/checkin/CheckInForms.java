package com.kayogx.eventcard.checkin;

import com.kayogx.eventcard.guest.RsvpStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** The shapes of check-in data sent to and from the API. */
public final class CheckInForms {

    private CheckInForms() {
    }

    /** What the door screen shows after a scan. */
    public enum LookUpStatus {
        /** Nobody on this card has arrived yet. */
        READY,
        /** Some people on this card arrived; there are seats left. */
        PARTLY_ARRIVED,
        /** Everyone on this card already arrived - do not let more people in. */
        ALL_ARRIVED,
        /** The code belongs to another event (or is not a real card). */
        NOT_FOR_THIS_EVENT
    }

    /** A guest as the door staff see them. */
    public record ArrivalGuest(UUID id, String nameOnCard, String cardTypeName, int seats, int peopleArrived,
                               int seatsLeft, Instant lastArrivedAt, RsvpStatus rsvpStatus, Integer rsvpPeople,
                               String groupName, String notes) {
    }

    /** {@code guest} is empty when the status is NOT_FOR_THIS_EVENT. */
    public record LookUpResult(LookUpStatus status, ArrivalGuest guest) {
    }

    public record LookUpRequest(

            @NotBlank(message = "Nothing was scanned")
            String scanned
    ) {
    }

    public record CheckInRequest(

            @NotNull(message = "Please choose the guest")
            UUID guestId,

            @Min(value = 1, message = "At least 1 person must enter")
            int people,

            @NotNull
            CheckIn.Method method
    ) {
    }

    public record CheckInDetails(UUID id, UUID guestId, String guestName, int people, CheckIn.Method method,
                                 String checkedInBy, Instant createdAt, boolean undone) {
    }

    public record CardTypeArrivals(String cardTypeName, long cards, long cardsArrived, long peopleArrived, long seats) {
    }

    /** The live numbers for the event: who is in, out of how many. */
    public record ArrivalSummary(long peopleArrived, long totalSeats, long cardsArrived, long totalCards,
                                 long expectedFromRsvp, List<CardTypeArrivals> byCardType,
                                 List<CheckInDetails> recentCheckIns) {
    }
}
