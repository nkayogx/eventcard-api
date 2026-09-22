package com.kayogx.eventcard.billing;

import com.kayogx.eventcard.billing.BillingForms.PaymentDetails;
import com.kayogx.eventcard.company.Company;

/** Turns a payment into what the API sends back. Shared by the company and platform admin screens. */
final class PaymentViews {

    private PaymentViews() {
    }

    /** @param companyOrNull filled in only for the platform admin, who sees payments of every company */
    static PaymentDetails detailsOf(Payment payment, PaymentProvider paymentProvider, Company companyOrNull) {
        PaymentInstructions instructions = payment.isWaiting() ? paymentProvider.instructionsFor(payment) : null;
        return new PaymentDetails(payment.getId(), payment.getReference(), payment.getType(), payment.getDescription(),
                payment.getAmountTzs(), payment.getStatus(), payment.getPayerPhone(), payment.getTransactionReference(),
                payment.getSubmittedAt(), payment.getConfirmedAt(), payment.getAdminNote(), payment.getCreatedAt(),
                instructions,
                companyOrNull == null ? null : companyOrNull.getId(),
                companyOrNull == null ? null : companyOrNull.getName());
    }
}
