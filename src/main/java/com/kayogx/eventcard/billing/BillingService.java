package com.kayogx.eventcard.billing;

import com.kayogx.eventcard.auth.LoggedInUser;
import com.kayogx.eventcard.billing.BillingForms.*;
import com.kayogx.eventcard.common.ConflictException;
import com.kayogx.eventcard.common.InvalidInputException;
import com.kayogx.eventcard.common.NotFoundException;
import com.kayogx.eventcard.common.RandomCodes;
import com.kayogx.eventcard.company.Company;
import com.kayogx.eventcard.company.CurrentCompany;
import com.kayogx.eventcard.event.EventRepository;
import com.kayogx.eventcard.event.EventStatus;
import com.kayogx.eventcard.tenant.AllCompaniesTransaction;
import com.kayogx.eventcard.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** The company's side of billing: see the plan and credits, buy, and say "I have paid". */
@Service
public class BillingService {

    private static final int MOVEMENTS_PER_PAGE = 30;

    private final CurrentCompany currentCompany;
    private final CurrentPlan currentPlan;
    private final PlanRepository planRepository;
    private final CreditPackRepository creditPackRepository;
    private final MessagePriceRepository messagePriceRepository;
    private final PaymentRepository paymentRepository;
    private final CreditMovementRepository movementRepository;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final PaymentProvider paymentProvider;
    private final AllCompaniesTransaction allCompaniesTransaction;

    public BillingService(CurrentCompany currentCompany,
                          CurrentPlan currentPlan,
                          PlanRepository planRepository,
                          CreditPackRepository creditPackRepository,
                          MessagePriceRepository messagePriceRepository,
                          PaymentRepository paymentRepository,
                          CreditMovementRepository movementRepository,
                          EventRepository eventRepository,
                          UserRepository userRepository,
                          PaymentProvider paymentProvider,
                          AllCompaniesTransaction allCompaniesTransaction) {
        this.currentCompany = currentCompany;
        this.currentPlan = currentPlan;
        this.planRepository = planRepository;
        this.creditPackRepository = creditPackRepository;
        this.messagePriceRepository = messagePriceRepository;
        this.paymentRepository = paymentRepository;
        this.movementRepository = movementRepository;
        this.eventRepository = eventRepository;
        this.userRepository = userRepository;
        this.paymentProvider = paymentProvider;
        this.allCompaniesTransaction = allCompaniesTransaction;
    }

    @Transactional(readOnly = true)
    public BillingOverview overview() {
        Company company = currentCompany.get();
        CurrentPlan.PlanState state = currentPlan.stateOf(company);
        Plan plan = state.plan();

        Usage usage = new Usage(eventRepository.countByStatus(EventStatus.ACTIVE), plan.getMaxActiveEvents(),
                userRepository.countByCompanyIdAndActiveTrue(company.getId()), plan.getMaxStaff(),
                plan.getMaxGuestsPerEvent());
        String chosenPlanName = company.getPlanId() == null ? null
                : planRepository.findById(company.getPlanId()).map(Plan::getName).orElse(null);

        List<PaymentDetails> waiting = paymentRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(Payment::isWaiting)
                .map(this::detailsOf)
                .toList();

        return new BillingOverview(PlanDetails.from(plan), state.status(), state.paidUntil(), state.graceEndsOn(),
                chosenPlanName, usage, company.getCreditBalance(),
                planRepository.findByAvailableTrueOrderBySortOrderAsc().stream().map(PlanDetails::from).toList(),
                creditPackRepository.findByAvailableTrueOrderBySortOrderAsc().stream().map(CreditPackDetails::from).toList(),
                messagePriceRepository.findAll().stream().map(MessagePriceDetails::from).toList(),
                waiting);
    }

    /** Starts buying a plan or a credit pack. Answers with how to pay. */
    @Transactional
    public PaymentDetails startPayment(NewPaymentRequest request) {
        Company company = currentCompany.get();
        Payment payment = new Payment();
        payment.setCompanyId(company.getId());
        payment.setType(request.type());
        payment.setCreatedByUserId(LoggedInUser.current().userId());
        payment.setMethod(paymentProvider.method());
        payment.setReference(newUniqueReference());

        switch (request.type()) {
            case PLAN -> describePlanPurchase(payment, request);
            case CREDITS -> describeCreditPurchase(payment, request);
        }
        paymentRepository.save(payment);
        return detailsOf(payment);
    }

    /** "I have paid" - the company gives the transaction code so the platform admin can find the money. */
    @Transactional
    public PaymentDetails submitPayment(UUID paymentId, SubmitPaymentRequest request) {
        Payment payment = findWaitingPayment(paymentId);
        payment.setPayerPhone(request.payerPhone().trim());
        payment.setTransactionReference(request.transactionReference().trim().toUpperCase());
        payment.setSubmittedAt(Instant.now());
        return detailsOf(payment);
    }

    @Transactional
    public PaymentDetails cancelPayment(UUID paymentId) {
        Payment payment = findWaitingPayment(paymentId);
        payment.setStatus(Payment.Status.CANCELLED);
        return detailsOf(payment);
    }

    @Transactional(readOnly = true)
    public List<PaymentDetails> paymentHistory() {
        return paymentRepository.findAllByOrderByCreatedAtDesc().stream().map(this::detailsOf).toList();
    }

    @Transactional(readOnly = true)
    public CreditStatement creditStatement(int page) {
        Page<CreditMovement> result = movementRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(page, MOVEMENTS_PER_PAGE));
        return new CreditStatement(result.getContent().stream().map(CreditMovementDetails::from).toList(),
                result.getNumber(), result.getTotalPages());
    }

    // ---------- helpers ----------

    private void describePlanPurchase(Payment payment, NewPaymentRequest request) {
        if (request.planId() == null || request.months() == null) {
            throw new InvalidInputException("Please choose a plan and how many months", "planId");
        }
        Plan plan = planRepository.findById(request.planId())
                .filter(Plan::isAvailable)
                .orElseThrow(() -> new InvalidInputException("This plan is not available", "planId"));
        if (plan.isFreePlan()) {
            throw new InvalidInputException("The Free plan does not need to be bought", "planId");
        }
        payment.setPlanId(plan.getId());
        payment.setMonths(request.months());
        payment.setAmountTzs(plan.getMonthlyPriceTzs() * request.months());
        payment.setDescription(plan.getName() + " plan - " + request.months() + (request.months() == 1 ? " month" : " months"));
    }

    private void describeCreditPurchase(Payment payment, NewPaymentRequest request) {
        CreditPack pack = request.creditPackId() == null ? null : creditPackRepository.findById(request.creditPackId())
                .filter(CreditPack::isAvailable)
                .orElse(null);
        if (pack == null) {
            throw new InvalidInputException("Please choose one of the credit packs on sale", "creditPackId");
        }
        payment.setCreditPackId(pack.getId());
        payment.setCredits(pack.getCredits());
        payment.setAmountTzs(pack.getPriceTzs());
        payment.setDescription(pack.getCredits() + " message credits");
    }

    private Payment findWaitingPayment(UUID paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new NotFoundException("Payment not found"));
        if (!payment.isWaiting()) {
            throw new ConflictException("This payment is already " + payment.getStatus().name().toLowerCase().replace('_', ' '));
        }
        return payment;
    }

    /** References must be unique across ALL companies, so we check everywhere. */
    private String newUniqueReference() {
        String reference;
        do {
            reference = RandomCodes.newPaymentReference();
        } while (isReferenceTaken(reference));
        return reference;
    }

    private boolean isReferenceTaken(String reference) {
        return allCompaniesTransaction.run(() -> paymentRepository.existsByReference(reference));
    }

    PaymentDetails detailsOf(Payment payment) {
        return PaymentViews.detailsOf(payment, paymentProvider, null);
    }
}
