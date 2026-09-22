package com.kayogx.eventcard.billing;

import com.kayogx.eventcard.common.ConflictException;
import com.kayogx.eventcard.company.Company;
import com.kayogx.eventcard.event.EventRepository;
import com.kayogx.eventcard.event.EventStatus;
import com.kayogx.eventcard.guest.GuestRepository;
import com.kayogx.eventcard.user.UserRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Checks a company's plan before it does something the plan may not allow.
 * Every refusal is a 409 with {@code field = "plan"}, so the website can show an "Upgrade" link.
 *
 * Only NEW actions are checked. Nothing that already exists is ever blocked or deleted.
 */
@Component
public class PlanLimits {

    /** The "field" on plan-limit errors, so the website knows to offer an upgrade. */
    public static final String PLAN_FIELD = "plan";

    private final CurrentPlan currentPlan;
    private final EventRepository eventRepository;
    private final GuestRepository guestRepository;
    private final UserRepository userRepository;

    public PlanLimits(CurrentPlan currentPlan,
                      EventRepository eventRepository,
                      GuestRepository guestRepository,
                      UserRepository userRepository) {
        this.currentPlan = currentPlan;
        this.eventRepository = eventRepository;
        this.guestRepository = guestRepository;
        this.userRepository = userRepository;
    }

    /** Drafts are unlimited - only ACTIVE events count. */
    public void checkCanActivateEvent(Company company) {
        Plan plan = currentPlan.planOf(company);
        if (plan.getMaxActiveEvents() != null
                && eventRepository.countByStatus(EventStatus.ACTIVE) >= plan.getMaxActiveEvents()) {
            throw refusal("Your " + plan.getName() + " plan allows " + plural(plan.getMaxActiveEvents(), "active event")
                    + " at a time. Finish an event or upgrade your plan to activate more.");
        }
    }

    public void checkCanAddGuests(Company company, UUID eventId, int newGuests) {
        Integer remaining = remainingGuests(company, eventId);
        if (remaining != null && newGuests > remaining) {
            Plan plan = currentPlan.planOf(company);
            throw refusal("Your " + plan.getName() + " plan allows " + plural(plan.getMaxGuestsPerEvent(), "guest")
                    + " per event. You can add " + plural(Math.max(0, remaining), "more guest")
                    + ". Upgrade your plan to add more.");
        }
    }

    /** How many more guests this event may get on the current plan, or null if unlimited. */
    public Integer remainingGuests(Company company, UUID eventId) {
        Plan plan = currentPlan.planOf(company);
        if (plan.getMaxGuestsPerEvent() == null) {
            return null;
        }
        return Math.max(0, plan.getMaxGuestsPerEvent() - (int) guestRepository.countByEventId(eventId));
    }

    /** Staff means everyone who can log in to the company, including the owner. */
    public void checkCanAddStaff(Company company) {
        Plan plan = currentPlan.planOf(company);
        if (plan.getMaxStaff() != null
                && userRepository.countByCompanyIdAndActiveTrue(company.getId()) >= plan.getMaxStaff()) {
            throw refusal("Your " + plan.getName() + " plan allows " + plural(plan.getMaxStaff(), "person", "people")
                    + " to log in. Upgrade your plan to add more staff.");
        }
    }

    public void checkCustomDomainAllowed(Company company) {
        Plan plan = currentPlan.planOf(company);
        if (!plan.isAllowsCustomDomain()) {
            throw refusal("Custom domains are not included in your " + plan.getName() + " plan. Upgrade to use your own domain.");
        }
    }

    public void checkOwnArtworkAllowed(Company company) {
        Plan plan = currentPlan.planOf(company);
        if (!plan.isAllowsOwnArtwork()) {
            throw refusal("Uploading your own card design is not included in your " + plan.getName()
                    + " plan. Use one of our templates, or upgrade your plan.");
        }
    }

    private static ConflictException refusal(String message) {
        return new ConflictException(message, PLAN_FIELD);
    }

    private static String plural(int count, String word) {
        return plural(count, word, word + "s");
    }

    private static String plural(int count, String singular, String pluralWord) {
        return count + " " + (count == 1 ? singular : pluralWord);
    }
}
