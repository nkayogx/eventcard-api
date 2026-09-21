package com.kayogx.eventcard.invitation;

import com.kayogx.eventcard.event.EventType;
import com.kayogx.eventcard.guest.RsvpStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** The shapes of data on a guest's public invitation page. */
public final class InvitationForms {

    private InvitationForms() {
    }

    /** Everything the guest's personal page shows. */
    public record InvitationPage(
            String guestName,
            String cardTypeName,
            int seats,
            String cardImagePath,
            RsvpStatus rsvpStatus,
            Integer rsvpPeople,
            String rsvpMessage,
            boolean rsvpOpen,
            LocalDate rsvpDeadline,
            EventInfo event,
            CompanyInfo company
    ) {
    }

    public record EventInfo(
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
            String contactPhone
    ) {
    }

    public record CompanyInfo(String name, String logoUrl, String primaryColor, String secondaryColor) {
    }

    /** The guest's answer. {@code people} is needed only when attending. */
    public record RsvpRequest(

            @NotNull(message = "Please say whether you will attend")
            Boolean attending,

            Integer people,

            @Size(max = 300, message = "The message can be at most 300 characters")
            String message
    ) {
    }
}
