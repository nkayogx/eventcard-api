package com.kayogx.eventcard.dto;

import com.kayogx.eventcard.model.UserRole;

/** What the invited person sees before accepting: who invited them and as what. */
public record InvitationDetails(String companyName, String email, UserRole role) {
}
