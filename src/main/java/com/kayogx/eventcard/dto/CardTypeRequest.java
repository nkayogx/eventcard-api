package com.kayogx.eventcard.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CardTypeRequest(

        @NotBlank(message = "Please enter a name for the card type, e.g. VIP")
        @Size(max = 40, message = "Card type name must be at most 40 characters")
        String name,

        @Min(value = 1, message = "A card must have at least 1 seat")
        @Max(value = 50, message = "A card can have at most 50 seats")
        int seats
) {
}
