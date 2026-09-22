package com.kayogx.eventcard.checkin;

import com.kayogx.eventcard.auth.LoggedInUser;
import com.kayogx.eventcard.checkin.CheckInForms.*;
import com.kayogx.eventcard.common.ConflictException;
import com.kayogx.eventcard.common.NotFoundException;
import com.kayogx.eventcard.event.CardType;
import com.kayogx.eventcard.event.CardTypeRepository;
import com.kayogx.eventcard.event.Event;
import com.kayogx.eventcard.event.EventFinder;
import com.kayogx.eventcard.event.EventStatus;
import com.kayogx.eventcard.event.EventTotals;
import com.kayogx.eventcard.guest.Guest;
import com.kayogx.eventcard.guest.GuestRepository;
import com.kayogx.eventcard.user.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Letting guests in at the door: look up a scanned card, check people in,
 * search by name, see the live numbers, and undo mistakes.
 */
@Service
public class CheckInService {

    private static final DateTimeFormatter CLOCK_TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final EventFinder eventFinder;
    private final GuestRepository guestRepository;
    private final CardTypeRepository cardTypeRepository;
    private final CheckInRepository checkInRepository;
    private final UserRepository userRepository;
    private final EventTotals eventTotals;

    public CheckInService(EventFinder eventFinder,
                          GuestRepository guestRepository,
                          CardTypeRepository cardTypeRepository,
                          CheckInRepository checkInRepository,
                          UserRepository userRepository,
                          EventTotals eventTotals) {
        this.eventFinder = eventFinder;
        this.guestRepository = guestRepository;
        this.cardTypeRepository = cardTypeRepository;
        this.checkInRepository = checkInRepository;
        this.userRepository = userRepository;
        this.eventTotals = eventTotals;
    }

    /** What was scanned: who is it, and how many of them may still come in? Nothing is saved. */
    @Transactional(readOnly = true)
    public LookUpResult lookUp(UUID eventId, String scanned) {
        Event event = findEventOpenForCheckIn(eventId);
        String code = invitationCodeFrom(scanned);

        // Guests of other companies are invisible here, so their codes are simply "not for this event"
        Optional<Guest> guest = guestRepository.findByInvitationCode(code)
                .filter(found -> found.getEventId().equals(event.getId()));
        if (guest.isEmpty()) {
            return new LookUpResult(LookUpStatus.NOT_FOR_THIS_EVENT, null);
        }

        ArrivalGuest arrival = arrivalOf(guest.get());
        LookUpStatus status = arrival.seatsLeft() == 0 ? LookUpStatus.ALL_ARRIVED
                : arrival.peopleArrived() > 0 ? LookUpStatus.PARTLY_ARRIVED
                : LookUpStatus.READY;
        return new LookUpResult(status, arrival);
    }

    /** Lets people in. Refused if the card has fewer seats left than people entering. */
    @Transactional
    public ArrivalGuest checkIn(UUID eventId, CheckInRequest request) {
        Event event = findEventOpenForCheckIn(eventId);
        // Locked, so another gate cannot use the same seats at the same moment
        Guest guest = guestRepository.findByIdAndEventIdAndLockIt(request.guestId(), eventId)
                .orElseThrow(() -> new NotFoundException("Guest not found"));
        int seats = seatsOf(guest);
        int seatsLeft = seats - guest.getPeopleArrived();

        if (seatsLeft == 0) {
            throw new ConflictException("All " + seats + " already arrived at " + clockTime(guest.getLastArrivedAt(), event));
        }
        if (request.people() > seatsLeft) {
            throw new ConflictException("Only " + seatsLeft + (seatsLeft == 1 ? " seat is" : " seats are") + " left on this card", "people");
        }

        Instant now = Instant.now();
        guest.setPeopleArrived(guest.getPeopleArrived() + request.people());
        if (guest.getFirstArrivedAt() == null) {
            guest.setFirstArrivedAt(now);
        }
        guest.setLastArrivedAt(now);

        CheckIn checkIn = new CheckIn();
        checkIn.setCompanyId(event.getCompanyId());
        checkIn.setEventId(eventId);
        checkIn.setGuestId(guest.getId());
        checkIn.setPeople(request.people());
        checkIn.setMethod(request.method());
        checkIn.setCheckedInByUserId(LoggedInUser.current().userId());
        checkInRepository.save(checkIn);
        return arrivalOf(guest);
    }

    /** For guests without their card: find them by name or phone. */
    @Transactional(readOnly = true)
    public List<ArrivalGuest> search(UUID eventId, String searchText) {
        eventFinder.findEvent(eventId);
        if (searchText == null || searchText.isBlank()) {
            return List.of();
        }
        String pattern = "%" + searchText.trim().toLowerCase() + "%";
        return guestRepository.searchInEvent(eventId, pattern, PageRequest.of(0, 20)).stream()
                .map(this::arrivalOf)
                .toList();
    }

    @Transactional(readOnly = true)
    public ArrivalSummary summary(UUID eventId) {
        Event event = eventFinder.findEvent(eventId);
        Map<UUID, CardType> cardTypes = cardTypesOf(eventId);

        List<CardTypeArrivals> byCardType = new ArrayList<>();
        long peopleArrived = 0, totalSeats = 0, cardsArrived = 0, totalCards = 0;
        for (Object[] row : guestRepository.countArrivalsPerCardType(eventId)) {
            CardType cardType = cardTypes.get((UUID) row[0]);
            long cards = (Long) row[1];
            long cardsWithArrivals = ((Number) row[2]).longValue();
            long people = ((Number) row[3]).longValue();
            long seats = cards * cardType.getSeats();
            byCardType.add(new CardTypeArrivals(cardType.getName(), cards, cardsWithArrivals, people, seats));

            peopleArrived += people;
            totalSeats += seats;
            cardsArrived += cardsWithArrivals;
            totalCards += cards;
        }

        return new ArrivalSummary(peopleArrived, totalSeats, cardsArrived, totalCards,
                eventTotals.rsvpTotals(event.getId()).attendingPeople(), byCardType, recentCheckIns(eventId));
    }

    /** Undoes a mistaken check-in: the seats become free again. */
    @Transactional
    public ArrivalGuest undo(UUID eventId, UUID checkInId) {
        findEventOpenForCheckIn(eventId);
        CheckIn checkIn = checkInRepository.findByIdAndEventId(checkInId, eventId)
                .orElseThrow(() -> new NotFoundException("Check-in not found"));
        if (checkIn.isUndone()) {
            throw new ConflictException("This check-in was already undone");
        }
        Guest guest = guestRepository.findByIdAndEventIdAndLockIt(checkIn.getGuestId(), eventId)
                .orElseThrow(() -> new NotFoundException("Guest not found"));

        guest.setPeopleArrived(Math.max(0, guest.getPeopleArrived() - checkIn.getPeople()));
        if (guest.getPeopleArrived() == 0) {
            guest.setFirstArrivedAt(null);
            guest.setLastArrivedAt(null);
        }
        checkIn.setUndoneAt(Instant.now());
        checkIn.setUndoneByUserId(LoggedInUser.current().userId());
        return arrivalOf(guest);
    }

    // ---------- helpers ----------

    private Event findEventOpenForCheckIn(UUID eventId) {
        Event event = eventFinder.findEvent(eventId);
        if (event.getStatus() != EventStatus.ACTIVE) {
            throw new ConflictException("Check-in is only possible while the event is active");
        }
        return event;
    }

    /**
     * The QR code holds the guest's whole personal link, e.g. "https://invites.kayo.co.tz/i/Xk9p2QmT7aBc".
     * The invitation code is the part after the last "/" (a scanner may also give just the code).
     */
    static String invitationCodeFrom(String scanned) {
        String text = scanned.trim();
        int questionMark = text.indexOf('?');
        if (questionMark >= 0) {
            text = text.substring(0, questionMark);
        }
        while (text.endsWith("/")) {
            text = text.substring(0, text.length() - 1);
        }
        return text.substring(text.lastIndexOf('/') + 1);
    }

    private ArrivalGuest arrivalOf(Guest guest) {
        int seats = seatsOf(guest);
        CardType cardType = cardTypeRepository.findById(guest.getCardTypeId()).orElseThrow();
        return new ArrivalGuest(guest.getId(), guest.getNameOnCard(), cardType.getName(), seats,
                guest.getPeopleArrived(), Math.max(0, seats - guest.getPeopleArrived()), guest.getLastArrivedAt(),
                guest.getRsvpStatus(), guest.getRsvpPeople(), guest.getGroupName(), guest.getNotes());
    }

    private int seatsOf(Guest guest) {
        return cardTypeRepository.findById(guest.getCardTypeId()).map(CardType::getSeats).orElse(1);
    }

    private Map<UUID, CardType> cardTypesOf(UUID eventId) {
        Map<UUID, CardType> cardTypes = new HashMap<>();
        cardTypeRepository.findByEventIdOrderBySortOrderAsc(eventId).forEach(type -> cardTypes.put(type.getId(), type));
        return cardTypes;
    }

    private List<CheckInDetails> recentCheckIns(UUID eventId) {
        List<CheckIn> checkIns = checkInRepository.findTop20ByEventIdOrderByCreatedAtDesc(eventId);
        Map<UUID, String> guestNames = new HashMap<>();
        guestRepository.findAllById(checkIns.stream().map(CheckIn::getGuestId).toList())
                .forEach(guest -> guestNames.put(guest.getId(), guest.getNameOnCard()));
        Map<UUID, String> staffNames = new HashMap<>();
        userRepository.findAllById(checkIns.stream().map(CheckIn::getCheckedInByUserId).filter(Objects::nonNull).toList())
                .forEach(user -> staffNames.put(user.getId(), user.getFullName()));

        return checkIns.stream()
                .map(checkIn -> new CheckInDetails(checkIn.getId(), checkIn.getGuestId(), guestNames.get(checkIn.getGuestId()),
                        checkIn.getPeople(), checkIn.getMethod(), staffNames.get(checkIn.getCheckedInByUserId()),
                        checkIn.getCreatedAt(), checkIn.isUndone()))
                .toList();
    }

    private static String clockTime(Instant moment, Event event) {
        return moment == null ? "an earlier time" : CLOCK_TIME.format(moment.atZone(ZoneId.of(event.getTimeZone())));
    }
}
