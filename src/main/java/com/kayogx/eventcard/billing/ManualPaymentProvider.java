package com.kayogx.eventcard.billing;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

/**
 * The company pays to our Lipa Namba / till with mobile money, then types in the
 * transaction code from their SMS. The platform admin checks it arrived and confirms.
 *
 * Our payment details come from settings (app.payments.manual.*).
 */
@Component
public class ManualPaymentProvider implements PaymentProvider {

    private final String payToName;
    private final String payToNumber;
    private final String networks;

    public ManualPaymentProvider(@Value("${app.payments.manual.pay-to-name}") String payToName,
                                 @Value("${app.payments.manual.pay-to-number}") String payToNumber,
                                 @Value("${app.payments.manual.networks}") String networks) {
        this.payToName = payToName;
        this.payToNumber = payToNumber;
        this.networks = networks;
    }

    @Override
    public Payment.Method method() {
        return Payment.Method.MANUAL;
    }

    @Override
    public PaymentInstructions instructionsFor(Payment payment) {
        String amount = "TSh " + NumberFormat.getIntegerInstance(Locale.US).format(payment.getAmountTzs());
        List<String> steps = List.of(
                "Open your mobile money menu (" + networks + ").",
                "Choose \"Lipa kwa Simu\" / \"Pay by Lipa Namba\".",
                "Enter Lipa Namba " + payToNumber + " (" + payToName + ").",
                "Enter the amount: " + amount + ".",
                "If asked for a reference, write " + payment.getReference() + ".",
                "When you receive the confirmation SMS, type its transaction code below and press \"I have paid\".");
        return new PaymentInstructions("Pay " + amount + " with mobile money", steps, payment.getAmountTzs(),
                payment.getReference());
    }
}
