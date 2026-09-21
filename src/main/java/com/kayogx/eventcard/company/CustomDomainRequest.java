package com.kayogx.eventcard.company;

import jakarta.validation.constraints.NotBlank;

public record CustomDomainRequest(

        @NotBlank(message = "Please enter a domain, e.g. invites.yourcompany.com")
        String domain
) {
}
