package com.kayogx.eventcard.event;

import com.kayogx.eventcard.auth.LoggedInUser;
import com.kayogx.eventcard.billing.PlanLimits;
import com.kayogx.eventcard.card.CardDesignService;
import com.kayogx.eventcard.common.ConflictException;
import com.kayogx.eventcard.common.InvalidInputException;
import com.kayogx.eventcard.common.PhoneNumbers;
import com.kayogx.eventcard.company.Company;
import com.kayogx.eventcard.company.CurrentCompany;
import com.kayogx.eventcard.event.EventResponses.*;
import com.kayogx.eventcard.guest.GuestRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Creating, listing, editing and deleting events.
 * All events here belong to the logged-in user's company (filtered automatically).
 */
@Service
public class EventService {

    private static final int EVENTS_PER_PAGE = 20;

    private final EventRepository eventRepository;
    private final CardTypeRepository cardTypeRepository;
    private final GuestRepository guestRepository;
    private final EventFinder eventFinder;
    private final EventTotals eventTotals;
    private final CurrentCompany currentCompany;
    private final CardDesignService cardDesignService;
    private final PlanLimits planLimits;

    public EventService(EventRepository eventRepository,
                        CardTypeRepository cardTypeRepository,
                        GuestRepository guestRepository,
                        EventFinder eventFinder,
                        EventTotals eventTotals,
                        CurrentCompany currentCompany,
                        CardDesignService cardDesignService,
                        PlanLimits planLimits) {
        this.eventRepository = eventRepository;
        this.cardTypeRepository = cardTypeRepository;
        this.guestRepository = guestRepository;
        this.eventFinder = eventFinder;
        this.eventTotals = eventTotals;
        this.currentCompany = currentCompany;
        this.cardDesignService = cardDesignService;
        this.planLimits = planLimits;
    }

    /** Events sorted by start date. Both filters are optional. */
    @Transactional(readOnly = true)
    public EventPage listEvents(EventStatus statusFilter, String search, int page) {
        Specification<Event> filters = (event, query, conditions) -> {
            List<Predicate> rules = new ArrayList<>();
            if (statusFilter != null) {
                rules.add(conditions.equal(event.get("status"), statusFilter));
            }
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase() + "%";
                rules.add(conditions.or(
                        conditions.like(conditions.lower(event.get("name")), pattern),
                        conditions.like(conditions.lower(event.get("venueName")), pattern)));
            }
            return conditions.and(rules.toArray(new Predicate[0]));
        };

        Page<Event> result = eventRepository.findAll(filters,
                PageRequest.of(page, EVENTS_PER_PAGE, Sort.by("startsAt")));
        Map<UUID, EventTotals.Totals> totals = eventTotals.totalsForEvents(
                result.getContent().stream().map(Event::getId).toList());

        List<EventSummary> events = result.getContent().stream()
                .map(event -> {
                    EventTotals.Totals eventTotal = totals.getOrDefault(event.getId(), new EventTotals.Totals(0, 0));
                    return new EventSummary(event.getId(), event.getName(), event.getEventType(), event.getStartsAt(),
                            event.getVenueName(), event.getStatus(), eventTotal.cards(), eventTotal.seats());
                })
                .toList();
        return new EventPage(events, result.getNumber(), result.getTotalPages(), result.getTotalElements());
    }

    /** New events start as DRAFT, with two card types: Single (1 seat) and Double (2 seats). */
    @Transactional
    public EventDetails createEvent(EventRequest request) {
        Company company = currentCompany.get();

        Event event = new Event();
        event.setCompanyId(company.getId());
        event.setTimeZone(company.getTimeZone());
        event.setCreatedByUserId(LoggedInUser.current().userId());
        copyFormIntoEvent(request, event, company);
        eventRepository.save(event);

        addCardType(event, "Single", 1, 1);
        addCardType(event, "Double", 2, 2);
        return detailsOf(event);
    }

    @Transactional(readOnly = true)
    public EventDetails getEvent(UUID eventId) {
        return detailsOf(eventFinder.findEvent(eventId));
    }

    @Transactional
    public EventDetails updateEvent(UUID eventId, EventRequest request) {
        Event event = eventFinder.findChangeableEvent(eventId);
        copyFormIntoEvent(request, event, currentCompany.get());
        return detailsOf(event);
    }

    @Transactional
    public EventDetails changeStatus(UUID eventId, EventStatus newStatus) {
        Event event = eventFinder.findEvent(eventId);
        if (!event.getStatus().allowedNextStatuses().contains(newStatus)) {
            throw new ConflictException("A " + event.getStatus().name().toLowerCase()
                    + " event cannot be changed to " + newStatus.name().toLowerCase(), "status");
        }
        if (newStatus == EventStatus.ACTIVE) {
            planLimits.checkCanActivateEvent(currentCompany.get());
        }
        event.setStatus(newStatus);
        return detailsOf(event);
    }

    /** Only drafts can be deleted, so a live guest list is never lost by accident. Cancel instead. */
    @Transactional
    public void deleteEvent(UUID eventId) {
        Event event = eventFinder.findEvent(eventId);
        if (event.getStatus() != EventStatus.DRAFT) {
            throw new ConflictException("Only draft events can be deleted. Cancel this event instead.");
        }
        guestRepository.deleteAllByEventId(eventId);
        cardDesignService.deleteDesignOf(eventId);
        cardTypeRepository.deleteAllByEventId(eventId);
        eventRepository.delete(event);
    }

    private void copyFormIntoEvent(EventRequest request, Event event, Company company) {
        if (request.endsAt() != null && !request.endsAt().isAfter(request.startsAt())) {
            throw new InvalidInputException("The end time must be after the start time", "endsAt");
        }

        event.setName(request.name().trim());
        event.setEventType(request.eventType());
        event.setHostNames(blankToNull(request.hostNames()));
        event.setStartsAt(request.startsAt());
        event.setEndsAt(request.endsAt());
        event.setVenueName(request.venueName().trim());
        event.setVenueAddress(blankToNull(request.venueAddress()));
        event.setMapLink(blankToNull(request.mapLink()));
        event.setDressCode(blankToNull(request.dressCode()));
        event.setExtraInfo(blankToNull(request.extraInfo()));
        event.setContactPhone(cleanContactPhone(request.contactPhone(), company));
        event.setRsvpDeadline(request.rsvpDeadline());
    }

    private static String cleanContactPhone(String typedPhone, Company company) {
        if (typedPhone == null || typedPhone.isBlank()) {
            return null;
        }
        return PhoneNumbers.toInternationalFormat(typedPhone, company.getCountryCode())
                .orElseThrow(() -> new InvalidInputException("Phone number is not valid", "contactPhone"));
    }

    private void addCardType(Event event, String name, int seats, int sortOrder) {
        CardType cardType = new CardType();
        cardType.setCompanyId(event.getCompanyId());
        cardType.setEventId(event.getId());
        cardType.setName(name);
        cardType.setSeats(seats);
        cardType.setSortOrder(sortOrder);
        cardTypeRepository.save(cardType);
    }

    EventDetails detailsOf(Event event) {
        List<CardTypeDetails> cardTypes = eventTotals.cardTypesWithTotals(event.getId());
        long totalCards = cardTypes.stream().mapToLong(CardTypeDetails::cards).sum();
        long totalSeats = cardTypes.stream().mapToLong(CardTypeDetails::seatsUsed).sum();

        return new EventDetails(
                event.getId(), event.getName(), event.getEventType(), event.getHostNames(),
                event.getStartsAt(), event.getEndsAt(), event.getTimeZone(),
                event.getVenueName(), event.getVenueAddress(), event.getMapLink(),
                event.getDressCode(), event.getExtraInfo(), event.getContactPhone(), event.getRsvpDeadline(),
                event.getStatus(), event.getStatus().allowedNextStatuses(),
                cardTypes, eventTotals.groupTotals(event.getId()),
                totalCards, totalSeats, eventTotals.rsvpTotals(event.getId()));
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }
}
