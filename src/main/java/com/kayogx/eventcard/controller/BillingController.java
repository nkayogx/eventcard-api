package com.kayogx.eventcard.controller;

import com.kayogx.eventcard.service.BillingService;

import com.kayogx.eventcard.dto.BillingForms.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * The company's plan, credits and payments.
 * Owners and managers may LOOK; only the owner may BUY (it involves the company's money).
 */
@RestController
@RequestMapping("/api/billing")
@PreAuthorize("hasAnyRole('OWNER', 'MANAGER')")
public class BillingController {

    private final BillingService billingService;

    public BillingController(BillingService billingService) {
        this.billingService = billingService;
    }

    @GetMapping
    public BillingOverview overview() {
        return billingService.overview();
    }

    @PostMapping("/payments")
    @PreAuthorize("hasRole('OWNER')")
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentDetails startPayment(@Valid @RequestBody NewPaymentRequest request) {
        return billingService.startPayment(request);
    }

    @PutMapping("/payments/{paymentId}/submit")
    @PreAuthorize("hasRole('OWNER')")
    public PaymentDetails submitPayment(@PathVariable UUID paymentId, @Valid @RequestBody SubmitPaymentRequest request) {
        return billingService.submitPayment(paymentId, request);
    }

    @PostMapping("/payments/{paymentId}/cancel")
    @PreAuthorize("hasRole('OWNER')")
    public PaymentDetails cancelPayment(@PathVariable UUID paymentId) {
        return billingService.cancelPayment(paymentId);
    }

    @GetMapping("/payments")
    public List<PaymentDetails> paymentHistory() {
        return billingService.paymentHistory();
    }

    @GetMapping("/credit-movements")
    public CreditStatement creditStatement(@RequestParam(defaultValue = "0") int page) {
        return billingService.creditStatement(page);
    }
}
