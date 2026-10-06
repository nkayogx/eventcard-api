package com.kayogx.eventcard.config;

import com.kayogx.eventcard.model.Guest;
import com.kayogx.eventcard.repository.GuestRepository;
import com.kayogx.eventcard.service.AllCompaniesTransaction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * When the app starts: gives an invitation code to any guest who doesn't have one yet
 * (guests created before personal links existed). New guests get one automatically.
 */
@Component
@Slf4j
public class InvitationCodeFiller implements ApplicationRunner {

    private final GuestRepository guestRepository;
    private final AllCompaniesTransaction allCompaniesTransaction;

    public InvitationCodeFiller(GuestRepository guestRepository, AllCompaniesTransaction allCompaniesTransaction) {
        this.guestRepository = guestRepository;
        this.allCompaniesTransaction = allCompaniesTransaction;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        int filledIn = allCompaniesTransaction.run(() -> {
            List<Guest> guestsWithoutCode = guestRepository.findByInvitationCodeIsNull();
            for (Guest guest : guestsWithoutCode) {
                guest.giveInvitationCode();
                guest.setRsvpStatus(guest.getRsvpStatus());
            }
            return guestsWithoutCode.size();
        });
        if (filledIn > 0) {
            log.info("Gave invitation codes to {} existing guests", filledIn);
        }
    }
}
