package com.kayogx.eventcard.company;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** What the owner can change on the company profile page. */
public record UpdateCompanyRequest(

        @NotBlank(message = "Please enter the company name")
        @Size(max = 120, message = "Company name must be at most 120 characters")
        String name,

        @NotBlank(message = "Please enter a short name for links")
        @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$",
                message = "Short name may only contain lowercase letters, numbers and single hyphens, e.g. kayo-events")
        @Size(max = 60, message = "Short name must be at most 60 characters")
        String slug,

        @NotBlank(message = "Please enter a contact phone")
        @Pattern(regexp = "^\\+[0-9]{7,15}$", message = "Phone must be in international format, e.g. +255712345678")
        String contactPhone,

        @NotBlank(message = "Please enter a contact email")
        @Email(message = "Please enter a valid email address")
        String contactEmail,

        @Size(max = 255, message = "Address must be at most 255 characters")
        String address,

        @Size(max = 100, message = "City must be at most 100 characters")
        String city,

        @NotBlank(message = "Please choose your country")
        @Pattern(regexp = "^[A-Za-z]{2}$", message = "Country must be a two-letter code, e.g. TZ")
        String countryCode,

        @NotBlank(message = "Please choose your time zone")
        String timeZone,

        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Colour must look like #8B1E3F")
        String primaryColor,

        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Colour must look like #8B1E3F")
        String secondaryColor
) {
}
