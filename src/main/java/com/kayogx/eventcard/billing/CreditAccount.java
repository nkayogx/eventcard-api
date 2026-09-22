package com.kayogx.eventcard.billing;

import com.kayogx.eventcard.common.ConflictException;
import com.kayogx.eventcard.common.NotFoundException;
import com.kayogx.eventcard.company.Company;
import com.kayogx.eventcard.company.CompanyRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * The ONLY place that changes a company's credit balance.
 * Every change writes a line on the credit statement ({@link CreditMovement}),
 * so the balance can always be explained.
 *
 * The company's row is locked during a change, so two changes at the same moment
 * (e.g. a purchase and a message being sent) cannot overwrite each other.
 */
@Component
public class CreditAccount {

    private final CompanyRepository companyRepository;
    private final CreditMovementRepository movementRepository;

    public CreditAccount(CompanyRepository companyRepository, CreditMovementRepository movementRepository) {
        this.companyRepository = companyRepository;
        this.movementRepository = movementRepository;
    }

    /** Adds credits (amount must be positive). */
    @Transactional
    public CreditMovement add(UUID companyId, long amount, CreditMovement.Reason reason, String note,
                              UUID paymentId, UUID byUserId) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Use a positive amount to add credits");
        }
        return change(companyId, amount, reason, note, paymentId, byUserId);
    }

    /** Uses credits (amount must be positive). Refused if the company doesn't have enough. */
    @Transactional
    public CreditMovement spend(UUID companyId, long amount, CreditMovement.Reason reason, String note, UUID byUserId) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Use a positive amount to spend credits");
        }
        return change(companyId, -amount, reason, note, null, byUserId);
    }

    /** Adds (positive) or removes (negative) credits by hand - used by the platform admin. */
    @Transactional
    public CreditMovement adjust(UUID companyId, long amount, String note, UUID byUserId) {
        return change(companyId, amount, CreditMovement.Reason.ADMIN_ADJUSTMENT, note, null, byUserId);
    }

    private CreditMovement change(UUID companyId, long amount, CreditMovement.Reason reason, String note,
                                  UUID paymentId, UUID byUserId) {
        Company company = companyRepository.findByIdAndLockIt(companyId)
                .orElseThrow(() -> new NotFoundException("Company not found"));

        long newBalance = company.getCreditBalance() + amount;
        if (newBalance < 0) {
            throw new ConflictException("Not enough credits. The balance is " + company.getCreditBalance()
                    + " credits, but " + Math.abs(amount) + " are needed.", PlanLimits.PLAN_FIELD);
        }
        company.setCreditBalance(newBalance);

        CreditMovement movement = new CreditMovement();
        movement.setCompanyId(companyId);
        movement.setAmount(amount);
        movement.setReason(reason);
        movement.setBalanceAfter(newBalance);
        movement.setNote(note);
        movement.setPaymentId(paymentId);
        movement.setCreatedByUserId(byUserId);
        return movementRepository.save(movement);
    }
}
