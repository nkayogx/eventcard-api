package com.kayogx.eventcard.dto;

/**
 * Sent back after a successful signup, login or invitation acceptance.
 * The React app keeps the token and sends it with every later request.
 */
public record LoginResponse(String token, MeResponse me) {
}
