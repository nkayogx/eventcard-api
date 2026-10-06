package com.kayogx.eventcard.service;

import com.kayogx.eventcard.dto.PaymentInstructions;
import com.kayogx.eventcard.model.Payment;

/**
 * A way of collecting money. Today there is only {@link ManualPaymentProvider}.
 *
 * To add a mobile-money company later (AzamPay, Selcom, ClickPesa...), create another
 * class that implements this, e.g. by sending a payment prompt to the payer's phone.
 * Nothing else in the app needs to change.
 */
public interface PaymentProvider {

    Payment.Method method();

    /** What the company must do to pay. */
    PaymentInstructions instructionsFor(Payment payment);
}
