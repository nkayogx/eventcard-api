package com.kayogx.eventcard.service;

import com.kayogx.eventcard.exception.ConflictException;
import com.kayogx.eventcard.model.Company;
import com.kayogx.eventcard.model.CreditMovement;
import com.kayogx.eventcard.model.Payment;
import com.kayogx.eventcard.repository.CompanyRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

/**
 * What happens when the money for a payment has arrived: the plan or the credits are switched on.
 *
 * Today the platform admin triggers this by hand. Later, a mobile-money connector will call it
 * automatically when the payment company tells us the money arrived.
 */
@Component
public class PaymentConfirmer {

    private final CompanyRepository companyRepository;
    private final CreditAccount creditAccount;

    public PaymentConfirmer(CompanyRepository companyRepository, CreditAccount creditAccount) {
        this.companyRepository = companyRepository;
        this.creditAccount = creditAccount;
    }

    @Transactional
    public void confirm(Payment payment, UUID confirmedByUserId) {
        if (!payment.isWaiting()) {
            throw new ConflictException("This payment is already " + payment.getStatus().name().toLowerCase().replace('_', ' '));
        }

        switch (payment.getType()) {
            case PLAN -> startOrExtendPlan(payment);
            case CREDITS -> creditAccount.add(payment.getCompanyId(), payment.getCredits(),
                    CreditMovement.Reason.PURCHASE, payment.getDescription(), payment.getId(), confirmedByUserId);
        }

        payment.setStatus(Payment.Status.PAID);
        payment.setConfirmedAt(Instant.now());
        payment.setConfirmedByUserId(confirmedByUserId);
    }

    /**
     * Buying more months of the SAME plan while it is still running adds them on top.
     * Otherwise (new plan, or the old one ran out) the months start today.
     */
    private void startOrExtendPlan(Payment payment) {
        Company company = companyRepository.findById(payment.getCompanyId()).orElseThrow();
        LocalDate today = LocalDate.now(ZoneId.of(company.getTimeZone()));

        boolean samePlanStillRunning = payment.getPlanId().equals(company.getPlanId())
                && company.getPlanPaidUntil() != null
                && !company.getPlanPaidUntil().isBefore(today);
        LocalDate startFrom = samePlanStillRunning ? company.getPlanPaidUntil() : today;

        company.setPlanId(payment.getPlanId());
        company.setPlanPaidUntil(startFrom.plusMonths(payment.getMonths()));
    }
}
