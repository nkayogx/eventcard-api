package com.kayogx.eventcard.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** What the invited person fills in to create their account. */
public record AcceptInvitationRequest(

        @NotBlank(message = "Please enter your full name")
        String fullName,

        @Pattern(regexp = "^\\+[0-9]{7,15}$", message = "Phone must be in international format, e.g. +255712345678")
        String phone,

        @NotBlank(message = "Please choose a password")
        @Size(min = 8, message = "Password must be at least 8 characters")
        String password
) {
}
