package com.kayogx.eventcard.billing;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * When the app starts for the first time: creates the starting plans, credit packs
 * and message prices. The platform admin can change them all later in the app.
 * Nothing is created if they already exist.
 */
@Component
@Slf4j
public class BillingSeeder implements ApplicationRunner {

    private final PlanRepository planRepository;
    private final CreditPackRepository creditPackRepository;
    private final MessagePriceRepository messagePriceRepository;

    public BillingSeeder(PlanRepository planRepository,
                         CreditPackRepository creditPackRepository,
                         MessagePriceRepository messagePriceRepository) {
        this.planRepository = planRepository;
        this.creditPackRepository = creditPackRepository;
        this.messagePriceRepository = messagePriceRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        if (planRepository.count() == 0) {
            //                 code       name       price   events guests staff domain  artwork free   order
            planRepository.save(plan("FREE", "Free", 0, 1, 100, 1, false, false, true, 1));
            planRepository.save(plan("STARTER", "Starter", 30_000, 5, 500, 3, false, true, false, 2));
            planRepository.save(plan("PRO", "Pro", 80_000, null, 3_000, 10, true, true, false, 3));
            log.info("Created the starting plans: Free, Starter, Pro");
        }
        if (creditPackRepository.count() == 0) {
            creditPackRepository.save(pack("100 credits", 100, 6_000, 1));
            creditPackRepository.save(pack("500 credits", 500, 25_000, 2));
            creditPackRepository.save(pack("2,000 credits", 2_000, 90_000, 3));
        }
        if (messagePriceRepository.count() == 0) {
            messagePriceRepository.save(price(MessageChannel.SMS, 1));
            messagePriceRepository.save(price(MessageChannel.WHATSAPP, 2));
        }
    }

    private static Plan plan(String code, String name, long price, Integer maxEvents, Integer maxGuests, Integer maxStaff,
                             boolean customDomain, boolean ownArtwork, boolean free, int order) {
        Plan plan = new Plan();
        plan.setCode(code);
        plan.setName(name);
        plan.setMonthlyPriceTzs(price);
        plan.setMaxActiveEvents(maxEvents);
        plan.setMaxGuestsPerEvent(maxGuests);
        plan.setMaxStaff(maxStaff);
        plan.setAllowsCustomDomain(customDomain);
        plan.setAllowsOwnArtwork(ownArtwork);
        plan.setFreePlan(free);
        plan.setSortOrder(order);
        return plan;
    }

    private static CreditPack pack(String name, int credits, long price, int order) {
        CreditPack pack = new CreditPack();
        pack.setName(name);
        pack.setCredits(credits);
        pack.setPriceTzs(price);
        pack.setSortOrder(order);
        return pack;
    }

    private static MessagePrice price(MessageChannel channel, int credits) {
        MessagePrice price = new MessagePrice();
        price.setChannel(channel);
        price.setCredits(credits);
        return price;
    }
}
