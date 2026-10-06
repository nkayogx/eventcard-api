package com.kayogx.eventcard.dto;

import java.awt.image.BufferedImage;
import java.time.LocalDateTime;

/**
 * Everything that is printed on one guest's card.
 * Uploaded designs only use the guest part; templates also show the event and company details.
 */
public record CardContent(
        // The guest
        String guestName,
        String cardTypeName,
        int seats,
        String invitationLink,
        // The event (used by templates)
        String eventName,
        String hostNames,
        LocalDateTime startsAt,
        String venueName,
        String dressCode,
        // The company's look (used by templates)
        String primaryColor,
        String secondaryColor,
        BufferedImage logoOrNull
) {
}
