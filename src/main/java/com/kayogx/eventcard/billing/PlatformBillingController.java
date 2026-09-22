package com.kayogx.eventcard.billing;

import com.kayogx.eventcard.billing.BillingForms.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Platform admin only: plans, prices, checking payments and adjusting credits. */
@RestController
@RequestMapping("/api/platform")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class PlatformBillingController {

    private final PlatformBillingService billingService;

    public PlatformBillingController(PlatformBillingService billingService) {
        this.billingService = billingService;
    }

    // ---------- Plans ----------

    @GetMapping("/plans")
    public List<PlanDetails> listPlans() {
        return billingService.listPlans();
    }

    @PostMapping("/plans")
    @ResponseStatus(HttpStatus.CREATED)
    public PlanDetails createPlan(@Valid @RequestBody PlanRequest request) {
        return billingService.createPlan(request);
    }

    @PutMapping("/plans/{planId}")
    public PlanDetails updatePlan(@PathVariable UUID planId, @Valid @RequestBody PlanRequest request) {
        return billingService.updatePlan(planId, request);
    }

    // ---------- Credit packs and message prices ----------

    @GetMapping("/credit-packs")
    public List<CreditPackDetails> listCreditPacks() {
        return billingService.listCreditPacks();
    }

    @PostMapping("/credit-packs")
    @ResponseStatus(HttpStatus.CREATED)
    public CreditPackDetails createCreditPack(@Valid @RequestBody CreditPackRequest request) {
        return billingService.createCreditPack(request);
    }

    @PutMapping("/credit-packs/{packId}")
    public CreditPackDetails updateCreditPack(@PathVariable UUID packId, @Valid @RequestBody CreditPackRequest request) {
        return billingService.updateCreditPack(packId, request);
    }

    @GetMapping("/message-prices")
    public List<MessagePriceDetails> listMessagePrices() {
        return billingService.listMessagePrices();
    }

    @PutMapping("/message-prices/{channel}")
    public MessagePriceDetails updateMessagePrice(@PathVariable MessageChannel channel,
                                                  @Valid @RequestBody MessagePriceRequest request) {
        return billingService.updateMessagePrice(channel, request);
    }

    // ---------- Payments and credits ----------

    @GetMapping("/payments")
    public List<PaymentDetails> listPayments(@RequestParam(defaultValue = "WAITING_FOR_PAYMENT") Payment.Status status) {
        return billingService.listPayments(status);
    }

    @PutMapping("/payments/{paymentId}/confirm")
    public PaymentDetails confirmPayment(@PathVariable UUID paymentId) {
        return billingService.confirmPayment(paymentId);
    }

    @PutMapping("/payments/{paymentId}/reject")
    public PaymentDetails rejectPayment(@PathVariable UUID paymentId, @Valid @RequestBody RejectPaymentRequest request) {
        return billingService.rejectPayment(paymentId, request);
    }

    @PostMapping("/companies/{companyId}/credits")
    public CreditMovementDetails adjustCredits(@PathVariable UUID companyId, @Valid @RequestBody AdjustCreditsRequest request) {
        return billingService.adjustCredits(companyId, request);
    }
}
