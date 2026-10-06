package com.kayogx.eventcard.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** What a new vendor fills in on the "Sign up your company" form. */
public record SignupCompanyRequest(

        @NotBlank(message = "Please enter your company name")
        @Size(max = 120, message = "Company name must be at most 120 characters")
        String companyName,

        @NotBlank(message = "Please enter your full name")
        String fullName,

        @NotBlank(message = "Please enter your email")
        @Email(message = "Please enter a valid email address")
        String email,

        @NotBlank(message = "Please enter a phone number")
        @Pattern(regexp = "^\\+[0-9]{7,15}$", message = "Phone must be in international format, e.g. +255712345678")
        String phone,

        @NotBlank(message = "Please choose a password")
        @Size(min = 8, message = "Password must be at least 8 characters")
        String password,

        @NotBlank(message = "Please choose your country")
        @Pattern(regexp = "^[A-Za-z]{2}$", message = "Country must be a two-letter code, e.g. TZ")
        String countryCode,

        @NotBlank(message = "Please choose your time zone")
        String timeZone
) {
}
