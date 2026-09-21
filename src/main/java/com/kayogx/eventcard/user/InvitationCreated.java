package com.kayogx.eventcard.user;

import java.time.Instant;

/**
 * Answer after inviting someone. The owner copies {@code invitationLink}
 * and shares it (e.g. on WhatsApp) with the person being invited.
 */
public record InvitationCreated(String email, UserRole role, String invitationLink, Instant expiresAt) {
}
