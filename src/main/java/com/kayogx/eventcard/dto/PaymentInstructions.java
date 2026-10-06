package com.kayogx.eventcard.dto;

import java.util.List;

/**
 * How the company should pay, shown step by step on the payment screen.
 * {@code steps} are plain sentences, e.g. "Enter Lipa Namba 123456 (EventCard Ltd)".
 */
public record PaymentInstructions(String title, List<String> steps, long amountTzs, String reference) {
}
