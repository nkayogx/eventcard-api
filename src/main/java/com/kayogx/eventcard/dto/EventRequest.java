package com.kayogx.eventcard.dto;

import com.kayogx.eventcard.model.Event;
import com.kayogx.eventcard.model.EventType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** The event form, used both to create and to edit an event. */
public record EventRequest(

        @NotBlank(message = "Please enter the event name")
        @Size(max = 150, message = "Event name must be at most 150 characters")
        String name,

        @NotNull(message = "Please choose the type of event")
        EventType eventType,

        @Size(max = 150, message = "Host names must be at most 150 characters")
        String hostNames,

        @NotNull(message = "Please choose when the event starts")
        LocalDateTime startsAt,

        LocalDateTime endsAt,

        @NotBlank(message = "Please enter the venue")
        @Size(max = 150, message = "Venue name must be at most 150 characters")
        String venueName,

        @Size(max = 255, message = "Venue address must be at most 255 characters")
        String venueAddress,

        @Size(max = 500, message = "Map link must be at most 500 characters")
        @Pattern(regexp = "^https?://.+", message = "Map link must start with http:// or https://")
        String mapLink,

        @Size(max = 100, message = "Dress code must be at most 100 characters")
        String dressCode,

        @Size(max = 2000, message = "Extra information must be at most 2000 characters")
        String extraInfo,

        String contactPhone,

        LocalDate rsvpDeadline
) {
}
