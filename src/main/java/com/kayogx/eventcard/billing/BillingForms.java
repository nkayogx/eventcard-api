package com.kayogx.eventcard.billing;

import jakarta.validation.constraints.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** The shapes of billing data sent to and from the API. */
public final class BillingForms {

    private BillingForms() {
    }

    // ---------- What the API sends back ----------

    /** A plan and its limits. Empty (null) limits mean unlimited. */
    public record PlanDetails(UUID id, String code, String name, long monthlyPriceTzs,
                              Integer maxActiveEvents, Integer maxGuestsPerEvent, Integer maxStaff,
                              boolean allowsCustomDomain, boolean allowsOwnArtwork,
                              boolean freePlan, boolean available, int sortOrder) {

        static PlanDetails from(Plan plan) {
            return new PlanDetails(plan.getId(), plan.getCode(), plan.getName(), plan.getMonthlyPriceTzs(),
                    plan.getMaxActiveEvents(), plan.getMaxGuestsPerEvent(), plan.getMaxStaff(),
                    plan.isAllowsCustomDomain(), plan.isAllowsOwnArtwork(), plan.isFreePlan(),
                    plan.isAvailable(), plan.getSortOrder());
        }
    }

    public record CreditPackDetails(UUID id, String name, int credits, long priceTzs, boolean available, int sortOrder) {

        static CreditPackDetails from(CreditPack pack) {
            return new CreditPackDetails(pack.getId(), pack.getName(), pack.getCredits(), pack.getPriceTzs(),
                    pack.isAvailable(), pack.getSortOrder());
        }
    }

    public record MessagePriceDetails(MessageChannel channel, int credits) {

        static MessagePriceDetails from(MessagePrice price) {
            return new MessagePriceDetails(price.getChannel(), price.getCredits());
        }
    }

    /** How much of the plan the company is using. Empty (null) maximums mean unlimited. */
    public record Usage(long activeEvents, Integer maxActiveEvents, long staff, Integer maxStaff, Integer maxGuestsPerEvent) {
    }

    /**
     * Everything on the "Plan & credits" page.
     * {@code chosenPlanName} is the paid plan the company last had - shown as "Renew Pro" when it has run out.
     */
    public record BillingOverview(PlanDetails plan, CurrentPlan.Status planStatus, LocalDate paidUntil,
                                  LocalDate graceEndsOn, String chosenPlanName, Usage usage, long creditBalance,
                                  List<PlanDetails> plansForSale, List<CreditPackDetails> packsForSale,
                                  List<MessagePriceDetails> messagePrices, List<PaymentDetails> waitingPayments) {
    }

    /**
     * A payment. {@code instructions} is only filled in while it is waiting for the money.
     * {@code companyName} is only filled in for the platform admin.
     */
    public record PaymentDetails(UUID id, String reference, Payment.Type type, String description, long amountTzs,
                                 Payment.Status status, String payerPhone, String transactionReference,
                                 Instant submittedAt, Instant confirmedAt, String adminNote, Instant createdAt,
                                 PaymentInstructions instructions, UUID companyId, String companyName) {
    }

    /** One line of the credit statement. */
    public record CreditMovementDetails(UUID id, long amount, CreditMovement.Reason reason, long balanceAfter,
                                        String note, Instant createdAt) {

        static CreditMovementDetails from(CreditMovement movement) {
            return new CreditMovementDetails(movement.getId(), movement.getAmount(), movement.getReason(),
                    movement.getBalanceAfter(), movement.getNote(), movement.getCreatedAt());
        }
    }

    public record CreditStatement(List<CreditMovementDetails> movements, int page, int totalPages) {
    }

    // ---------- What the API receives ----------

    /** Buying a plan ({@code planId} + {@code months}) or a credit pack ({@code creditPackId}). */
    public record NewPaymentRequest(

            @NotNull(message = "Please choose what to buy")
            Payment.Type type,

            UUID planId,

            @Min(value = 1, message = "Choose between 1 and 12 months")
            @Max(value = 12, message = "Choose between 1 and 12 months")
            Integer months,

            UUID creditPackId
    ) {
    }

    /** "I have paid": the phone that paid and the transaction code from the SMS. */
    public record SubmitPaymentRequest(

            @NotBlank(message = "Please enter the phone number you paid with")
            @Size(max = 20, message = "Phone number is too long")
            String payerPhone,

            @NotBlank(message = "Please enter the transaction code from your SMS")
            @Size(max = 60, message = "Transaction code is too long")
            String transactionReference
    ) {
    }

    public record RejectPaymentRequest(

            @NotBlank(message = "Please say why the payment is rejected")
            @Size(max = 300, message = "The note can be at most 300 characters")
            String note
    ) {
    }

    public record AdjustCreditsRequest(

            long amount,

            @NotBlank(message = "Please add a note, e.g. \"Gift for launch\"")
            @Size(max = 200, message = "The note can be at most 200 characters")
            String note
    ) {
    }

    public record PlanRequest(

            @NotBlank(message = "Please enter a code, e.g. PRO")
            @Pattern(regexp = "^[A-Z0-9_]{2,30}$", message = "Code: 2-30 capital letters, digits or _")
            String code,

            @NotBlank(message = "Please enter the plan name")
            @Size(max = 60, message = "Name can be at most 60 characters")
            String name,

            @PositiveOrZero(message = "Price cannot be negative")
            long monthlyPriceTzs,

            @Positive(message = "Leave empty for unlimited, or enter at least 1")
            Integer maxActiveEvents,

            @Positive(message = "Leave empty for unlimited, or enter at least 1")
            Integer maxGuestsPerEvent,

            @Positive(message = "Leave empty for unlimited, or enter at least 1")
            Integer maxStaff,

            boolean allowsCustomDomain,
            boolean allowsOwnArtwork,
            boolean available,
            int sortOrder
    ) {
    }

    public record CreditPackRequest(

            @NotBlank(message = "Please enter the pack name")
            @Size(max = 60, message = "Name can be at most 60 characters")
            String name,

            @Positive(message = "A pack must have at least 1 credit")
            int credits,

            @Positive(message = "Price must be more than 0")
            long priceTzs,

            boolean available,
            int sortOrder
    ) {
    }

    public record MessagePriceRequest(

            @Min(value = 0, message = "Credits cannot be negative")
            @Max(value = 100, message = "At most 100 credits per message")
            int credits
    ) {
    }
}
