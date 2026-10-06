package com.kayogx.eventcard.service;

import com.kayogx.eventcard.exception.ConflictException;
import com.kayogx.eventcard.exception.InvalidInputException;
import com.kayogx.eventcard.exception.NotFoundException;
import com.kayogx.eventcard.model.Company;
import com.kayogx.eventcard.model.CreditPack;
import com.kayogx.eventcard.model.MessageChannel;
import com.kayogx.eventcard.model.MessagePrice;
import com.kayogx.eventcard.model.Payment;
import com.kayogx.eventcard.model.Plan;
import com.kayogx.eventcard.repository.CompanyRepository;
import com.kayogx.eventcard.repository.CreditPackRepository;
import com.kayogx.eventcard.repository.MessagePriceRepository;
import com.kayogx.eventcard.repository.PaymentRepository;
import com.kayogx.eventcard.repository.PlanRepository;
import com.kayogx.eventcard.security.LoggedInUser;
import com.kayogx.eventcard.dto.BillingForms.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * The platform admin's side of billing: set plans and prices, check payments, adjust credits.
 * The platform admin works "as all companies", so sees every company's payments.
 */
@Service
public class PlatformBillingService {

    private final PlanRepository planRepository;
    private final CreditPackRepository creditPackRepository;
    private final MessagePriceRepository messagePriceRepository;
    private final PaymentRepository paymentRepository;
    private final CompanyRepository companyRepository;
    private final PaymentConfirmer paymentConfirmer;
    private final CreditAccount creditAccount;
    private final PaymentProvider paymentProvider;

    public PlatformBillingService(PlanRepository planRepository,
                                  CreditPackRepository creditPackRepository,
                                  MessagePriceRepository messagePriceRepository,
                                  PaymentRepository paymentRepository,
                                  CompanyRepository companyRepository,
                                  PaymentConfirmer paymentConfirmer,
                                  CreditAccount creditAccount,
                                  PaymentProvider paymentProvider) {
        this.planRepository = planRepository;
        this.creditPackRepository = creditPackRepository;
        this.messagePriceRepository = messagePriceRepository;
        this.paymentRepository = paymentRepository;
        this.companyRepository = companyRepository;
        this.paymentConfirmer = paymentConfirmer;
        this.creditAccount = creditAccount;
        this.paymentProvider = paymentProvider;
    }

    // ---------- Plans ----------

    @Transactional(readOnly = true)
    public List<PlanDetails> listPlans() {
        return planRepository.findAllByOrderBySortOrderAsc().stream().map(PlanDetails::from).toList();
    }

    @Transactional
    public PlanDetails createPlan(PlanRequest request) {
        if (planRepository.findByCode(request.code()).isPresent()) {
            throw new ConflictException("A plan with this code already exists", "code");
        }
        Plan plan = new Plan();
        copyIntoPlan(request, plan);
        return PlanDetails.from(planRepository.save(plan));
    }

    @Transactional
    public PlanDetails updatePlan(UUID planId, PlanRequest request) {
        Plan plan = planRepository.findById(planId).orElseThrow(() -> new NotFoundException("Plan not found"));
        boolean codeTaken = planRepository.findByCode(request.code())
                .filter(other -> !other.getId().equals(planId))
                .isPresent();
        if (codeTaken) {
            throw new ConflictException("A plan with this code already exists", "code");
        }
        if (plan.isFreePlan() && (!request.available() || request.monthlyPriceTzs() != 0)) {
            throw new InvalidInputException("The Free plan must stay available and cost 0", "monthlyPriceTzs");
        }
        copyIntoPlan(request, plan);
        return PlanDetails.from(plan);
    }

    private static void copyIntoPlan(PlanRequest request, Plan plan) {
        plan.setCode(request.code());
        plan.setName(request.name().trim());
        plan.setMonthlyPriceTzs(request.monthlyPriceTzs());
        plan.setMaxActiveEvents(request.maxActiveEvents());
        plan.setMaxGuestsPerEvent(request.maxGuestsPerEvent());
        plan.setMaxStaff(request.maxStaff());
        plan.setAllowsCustomDomain(request.allowsCustomDomain());
        plan.setAllowsOwnArtwork(request.allowsOwnArtwork());
        plan.setAvailable(request.available());
        plan.setSortOrder(request.sortOrder());
    }

    // ---------- Credit packs and message prices ----------

    @Transactional(readOnly = true)
    public List<CreditPackDetails> listCreditPacks() {
        return creditPackRepository.findAllByOrderBySortOrderAsc().stream().map(CreditPackDetails::from).toList();
    }

    @Transactional
    public CreditPackDetails createCreditPack(CreditPackRequest request) {
        CreditPack pack = new CreditPack();
        copyIntoPack(request, pack);
        return CreditPackDetails.from(creditPackRepository.save(pack));
    }

    @Transactional
    public CreditPackDetails updateCreditPack(UUID packId, CreditPackRequest request) {
        CreditPack pack = creditPackRepository.findById(packId)
                .orElseThrow(() -> new NotFoundException("Credit pack not found"));
        copyIntoPack(request, pack);
        return CreditPackDetails.from(pack);
    }

    private static void copyIntoPack(CreditPackRequest request, CreditPack pack) {
        pack.setName(request.name().trim());
        pack.setCredits(request.credits());
        pack.setPriceTzs(request.priceTzs());
        pack.setAvailable(request.available());
        pack.setSortOrder(request.sortOrder());
    }

    @Transactional(readOnly = true)
    public List<MessagePriceDetails> listMessagePrices() {
        return messagePriceRepository.findAll().stream().map(MessagePriceDetails::from).toList();
    }

    @Transactional
    public MessagePriceDetails updateMessagePrice(MessageChannel channel, MessagePriceRequest request) {
        MessagePrice price = messagePriceRepository.findById(channel).orElseGet(() -> {
            MessagePrice newPrice = new MessagePrice();
            newPrice.setChannel(channel);
            return messagePriceRepository.save(newPrice);
        });
        price.setCredits(request.credits());
        return MessagePriceDetails.from(price);
    }

    // ---------- Payments ----------

    /** Payments with the given status (oldest first, so the longest-waiting are checked first). */
    @Transactional(readOnly = true)
    public List<PaymentDetails> listPayments(Payment.Status status) {
        return paymentRepository.findByStatusOrderByCreatedAtAsc(status).stream()
                .map(payment -> PaymentViews.detailsOf(payment, paymentProvider,
                        companyRepository.findById(payment.getCompanyId()).orElse(null)))
                .toList();
    }

    /** The money has arrived: switch on the plan or credits. */
    @Transactional
    public PaymentDetails confirmPayment(UUID paymentId) {
        Payment payment = findPayment(paymentId);
        paymentConfirmer.confirm(payment, LoggedInUser.current().userId());
        return PaymentViews.detailsOf(payment, paymentProvider, companyOf(payment));
    }

    @Transactional
    public PaymentDetails rejectPayment(UUID paymentId, RejectPaymentRequest request) {
        Payment payment = findPayment(paymentId);
        if (!payment.isWaiting()) {
            throw new ConflictException("This payment is already " + payment.getStatus().name().toLowerCase().replace('_', ' '));
        }
        payment.setStatus(Payment.Status.REJECTED);
        payment.setAdminNote(request.note().trim());
        payment.setConfirmedByUserId(LoggedInUser.current().userId());
        return PaymentViews.detailsOf(payment, paymentProvider, companyOf(payment));
    }

    /** Adds (e.g. a gift) or removes (e.g. a correction) credits by hand. */
    @Transactional
    public CreditMovementDetails adjustCredits(UUID companyId, AdjustCreditsRequest request) {
        if (request.amount() == 0) {
            throw new InvalidInputException("Enter a number of credits to add (e.g. 100) or remove (e.g. -100)", "amount");
        }
        return CreditMovementDetails.from(
                creditAccount.adjust(companyId, request.amount(), request.note().trim(), LoggedInUser.current().userId()));
    }

    private Payment findPayment(UUID paymentId) {
        return paymentRepository.findById(paymentId).orElseThrow(() -> new NotFoundException("Payment not found"));
    }

    private Company companyOf(Payment payment) {
        return companyRepository.findById(payment.getCompanyId()).orElse(null);
    }
}
