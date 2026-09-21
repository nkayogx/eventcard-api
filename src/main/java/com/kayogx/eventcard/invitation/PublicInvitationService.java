package com.kayogx.eventcard.invitation;

import com.kayogx.eventcard.card.CardMaker;
import com.kayogx.eventcard.common.ConflictException;
import com.kayogx.eventcard.common.InvalidInputException;
import com.kayogx.eventcard.common.NotFoundException;
import com.kayogx.eventcard.company.Company;
import com.kayogx.eventcard.company.CompanyRepository;
import com.kayogx.eventcard.event.CardType;
import com.kayogx.eventcard.event.CardTypeRepository;
import com.kayogx.eventcard.event.Event;
import com.kayogx.eventcard.event.EventRepository;
import com.kayogx.eventcard.event.EventStatus;
import com.kayogx.eventcard.guest.Guest;
import com.kayogx.eventcard.guest.GuestRepository;
import com.kayogx.eventcard.guest.RsvpStatus;
import com.kayogx.eventcard.invitation.InvitationForms.*;
import com.kayogx.eventcard.tenant.AllCompaniesTransaction;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * What a guest can do with their personal link - without logging in.
 * The secret invitation code in the link is the key: it points to exactly one guest.
 *
 * Guests don't belong to a logged-in company, so every lookup runs "as all companies",
 * and then only ever touches that one guest and their event.
 */
@Service
public class PublicInvitationService {

    private final GuestRepository guestRepository;
    private final EventRepository eventRepository;
    private final CardTypeRepository cardTypeRepository;
    private final CompanyRepository companyRepository;
    private final CardMaker cardMaker;
    private final WrongCodeLimiter wrongCodeLimiter;
    private final AllCompaniesTransaction allCompaniesTransaction;

    public PublicInvitationService(GuestRepository guestRepository,
                                   EventRepository eventRepository,
                                   CardTypeRepository cardTypeRepository,
                                   CompanyRepository companyRepository,
                                   CardMaker cardMaker,
                                   WrongCodeLimiter wrongCodeLimiter,
                                   AllCompaniesTransaction allCompaniesTransaction) {
        this.guestRepository = guestRepository;
        this.eventRepository = eventRepository;
        this.cardTypeRepository = cardTypeRepository;
        this.companyRepository = companyRepository;
        this.cardMaker = cardMaker;
        this.wrongCodeLimiter = wrongCodeLimiter;
        this.allCompaniesTransaction = allCompaniesTransaction;
    }

    /** One guest's invitation, with everything the page shows. */
    private record Invitation(Guest guest, Event event, CardType cardType, Company company) {
    }

    public InvitationPage showInvitation(String code, String internetAddress) {
        wrongCodeLimiter.checkNotBlocked(internetAddress);
        return allCompaniesTransaction.run(() -> pageOf(findInvitation(code, internetAddress)));
    }

    public byte[] cardImage(String code, String internetAddress) {
        wrongCodeLimiter.checkNotBlocked(internetAddress);
        return allCompaniesTransaction.run(() -> cardMaker.cardForGuest(findInvitation(code, internetAddress).guest()));
    }

    public InvitationPage answerRsvp(String code, String internetAddress, RsvpRequest answer) {
        wrongCodeLimiter.checkNotBlocked(internetAddress);
        return allCompaniesTransaction.run(() -> {
            Invitation invitation = findInvitation(code, internetAddress);
            if (!rsvpIsOpen(invitation.event())) {
                throw new ConflictException("Sorry, the RSVP deadline has passed. Please contact the hosts directly.");
            }

            Guest guest = invitation.guest();
            if (answer.attending()) {
                int seats = invitation.cardType().getSeats();
                Integer people = answer.people() == null ? seats : answer.people();
                if (people < 1 || people > seats) {
                    throw new InvalidInputException("Your card is for " + seats + (seats == 1 ? " person" : " people")
                            + ". Please choose between 1 and " + seats + ".", "people");
                }
                guest.setRsvpStatus(RsvpStatus.ATTENDING);
                guest.setRsvpPeople(people);
            } else {
                guest.setRsvpStatus(RsvpStatus.NOT_ATTENDING);
                guest.setRsvpPeople(null);
            }
            guest.setRsvpMessage(answer.message() == null || answer.message().isBlank() ? null : answer.message().trim());
            guest.setRsvpAnsweredAt(Instant.now());
            return pageOf(invitation);
        });
    }

    /**
     * Finds the invitation for a code. Unknown codes are counted (to stop guessing),
     * and invitations are only shown while their event is ACTIVE.
     */
    private Invitation findInvitation(String code, String internetAddress) {
        Guest guest = guestRepository.findByInvitationCode(code).orElse(null);
        if (guest == null) {
            wrongCodeLimiter.recordWrongCode(internetAddress);
            throw new NotFoundException("This invitation is not available");
        }
        Event event = eventRepository.findById(guest.getEventId()).orElseThrow();
        if (event.getStatus() != EventStatus.ACTIVE) {
            throw new NotFoundException("This invitation is not available");
        }
        CardType cardType = cardTypeRepository.findById(guest.getCardTypeId()).orElseThrow();
        Company company = companyRepository.findById(event.getCompanyId()).orElseThrow();
        return new Invitation(guest, event, cardType, company);
    }

    /** Guests can answer until the end of the deadline day, in the event's own time zone. */
    private static boolean rsvpIsOpen(Event event) {
        if (event.getRsvpDeadline() == null) {
            return true;
        }
        LocalDate todayAtTheVenue = LocalDate.now(ZoneId.of(event.getTimeZone()));
        return !todayAtTheVenue.isAfter(event.getRsvpDeadline());
    }

    private static InvitationPage pageOf(Invitation invitation) {
        Guest guest = invitation.guest();
        Event event = invitation.event();
        Company company = invitation.company();

        EventInfo eventInfo = new EventInfo(event.getName(), event.getEventType(), event.getHostNames(),
                event.getStartsAt(), event.getEndsAt(), event.getTimeZone(), event.getVenueName(),
                event.getVenueAddress(), event.getMapLink(), event.getDressCode(), event.getExtraInfo(),
                event.getContactPhone());
        CompanyInfo companyInfo = new CompanyInfo(company.getName(), company.getLogoUrl(),
                company.getPrimaryColor(), company.getSecondaryColor());

        return new InvitationPage(guest.getNameOnCard(), invitation.cardType().getName(), invitation.cardType().getSeats(),
                "/api/public/invitations/" + guest.getInvitationCode() + "/card.png",
                guest.getRsvpStatus(), guest.getRsvpPeople(), guest.getRsvpMessage(),
                rsvpIsOpen(event), event.getRsvpDeadline(), eventInfo, companyInfo);
    }
}
