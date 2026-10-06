package com.kayogx.eventcard.service;

import com.kayogx.eventcard.model.Company;
import com.kayogx.eventcard.model.Plan;
import com.kayogx.eventcard.repository.PlanRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Decides which plan a company is on RIGHT NOW.
 *
 *   - never paid (or chose Free)             -> Free plan        (status FREE)
 *   - paid, and today <= paid-until          -> their plan       (status ACTIVE)
 *   - paid-until passed, but within 7 days   -> still their plan (status IN_GRACE)
 *   - more than 7 days after paid-until      -> Free plan        (status EXPIRED)
 *
 * The company's chosen plan is kept, so paying again restores it at once.
 */
@Component
public class CurrentPlan {

    public static final int GRACE_DAYS = 7;

    public enum Status { FREE, ACTIVE, IN_GRACE, EXPIRED }

    /** The plan in force, why, and the key dates (empty for the Free plan). */
    public record PlanState(Plan plan, Status status, LocalDate paidUntil, LocalDate graceEndsOn) {
    }

    private final PlanRepository planRepository;

    public CurrentPlan(PlanRepository planRepository) {
        this.planRepository = planRepository;
    }

    public Plan planOf(Company company) {
        return stateOf(company).plan();
    }

    public PlanState stateOf(Company company) {
        Plan freePlan = freePlan();
        if (company.getPlanId() == null) {
            return new PlanState(freePlan, Status.FREE, null, null);
        }
        Plan chosenPlan = planRepository.findById(company.getPlanId()).orElse(freePlan);
        LocalDate paidUntil = company.getPlanPaidUntil();
        if (chosenPlan.isFreePlan() || paidUntil == null) {
            return new PlanState(freePlan, Status.FREE, null, null);
        }

        LocalDate today = LocalDate.now(ZoneId.of(company.getTimeZone()));
        LocalDate graceEndsOn = paidUntil.plusDays(GRACE_DAYS);
        if (!today.isAfter(paidUntil)) {
            return new PlanState(chosenPlan, Status.ACTIVE, paidUntil, graceEndsOn);
        }
        if (!today.isAfter(graceEndsOn)) {
            return new PlanState(chosenPlan, Status.IN_GRACE, paidUntil, graceEndsOn);
        }
        return new PlanState(freePlan, Status.EXPIRED, paidUntil, graceEndsOn);
    }

    public Plan freePlan() {
        return planRepository.findFirstByFreePlanTrue()
                .orElseThrow(() -> new IllegalStateException("No Free plan is set up"));
    }
}
