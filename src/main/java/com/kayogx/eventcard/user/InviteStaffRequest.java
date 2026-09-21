package com.kayogx.eventcard.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record InviteStaffRequest(

        @NotBlank(message = "Please enter the email of the person to invite")
        @Email(message = "Please enter a valid email address")
        String email,

        @NotNull(message = "Please choose a role")
        UserRole role
) {
}
