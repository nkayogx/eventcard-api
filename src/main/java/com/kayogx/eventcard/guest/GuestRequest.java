package com.kayogx.eventcard.guest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** The form for adding or editing one guest (one card). */
public record GuestRequest(

        @NotBlank(message = "Please enter the name as it should appear on the card")
        @Size(max = 150, message = "Name must be at most 150 characters")
        String nameOnCard,

        @NotBlank(message = "Please enter a phone number")
        String phone,

        @NotNull(message = "Please choose a card type")
        UUID cardTypeId,

        @Size(max = 60, message = "Group must be at most 60 characters")
        String groupName,

        @Size(max = 500, message = "Notes must be at most 500 characters")
        String notes
) {
}
