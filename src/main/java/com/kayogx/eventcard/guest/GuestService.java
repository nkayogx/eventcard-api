package com.kayogx.eventcard.guest;

import com.kayogx.eventcard.common.ConflictException;
import com.kayogx.eventcard.common.InvalidInputException;
import com.kayogx.eventcard.common.NotFoundException;
import com.kayogx.eventcard.common.PhoneNumbers;
import com.kayogx.eventcard.company.CurrentCompany;
import com.kayogx.eventcard.event.CardType;
import com.kayogx.eventcard.event.CardTypeRepository;
import com.kayogx.eventcard.event.Event;
import com.kayogx.eventcard.event.EventFinder;
import com.kayogx.eventcard.guest.GuestResponses.GuestDetails;
import com.kayogx.eventcard.guest.GuestResponses.GuestPage;
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
import java.util.function.Function;
import java.util.stream.Collectors;

/** Viewing, adding, editing and removing guests of an event, one at a time. */
@Service
public class GuestService {

    private static final int GUESTS_PER_PAGE = 50;

    private final GuestRepository guestRepository;
    private final CardTypeRepository cardTypeRepository;
    private final EventFinder eventFinder;
    private final CurrentCompany currentCompany;

    public GuestService(GuestRepository guestRepository,
                        CardTypeRepository cardTypeRepository,
                        EventFinder eventFinder,
                        CurrentCompany currentCompany) {
        this.guestRepository = guestRepository;
        this.cardTypeRepository = cardTypeRepository;
        this.eventFinder = eventFinder;
        this.currentCompany = currentCompany;
    }

    /** Guests sorted by name. Search (name or phone), card type and group filters are optional. */
    @Transactional(readOnly = true)
    public GuestPage listGuests(UUID eventId, String search, UUID cardTypeIdFilter, String groupFilter, int page) {
        eventFinder.findEvent(eventId);

        Specification<Guest> filters = (guest, query, conditions) -> {
            List<Predicate> rules = new ArrayList<>();
            rules.add(conditions.equal(guest.get("eventId"), eventId));
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase() + "%";
                rules.add(conditions.or(
                        conditions.like(conditions.lower(guest.get("nameOnCard")), pattern),
                        conditions.like(guest.get("phone"), pattern)));
            }
            if (cardTypeIdFilter != null) {
                rules.add(conditions.equal(guest.get("cardTypeId"), cardTypeIdFilter));
            }
            if (groupFilter != null && !groupFilter.isBlank()) {
                rules.add(conditions.equal(guest.get("groupName"), groupFilter));
            }
            return conditions.and(rules.toArray(new Predicate[0]));
        };

        Page<Guest> result = guestRepository.findAll(filters,
                PageRequest.of(page, GUESTS_PER_PAGE, Sort.by("nameOnCard")));
        Map<UUID, CardType> cardTypes = cardTypesOf(eventId);

        List<GuestDetails> guests = result.getContent().stream()
                .map(guest -> detailsOf(guest, cardTypes.get(guest.getCardTypeId())))
                .toList();
        return new GuestPage(guests, result.getNumber(), result.getTotalPages(), result.getTotalElements(),
                guestRepository.findGroupNames(eventId));
    }

    @Transactional
    public GuestDetails addGuest(UUID eventId, GuestRequest request) {
        Event event = eventFinder.findChangeableEvent(eventId);
        CardType cardType = findCardType(eventId, request.cardTypeId());
        String phone = cleanPhone(request.phone());

        if (guestRepository.existsByEventIdAndPhone(eventId, phone)) {
            throw new ConflictException("A guest with this phone number is already on the list", "phone");
        }

        Guest guest = new Guest();
        guest.setCompanyId(event.getCompanyId());
        guest.setEventId(eventId);
        copyFormIntoGuest(request, guest, phone);
        guestRepository.save(guest);
        return detailsOf(guest, cardType);
    }

    @Transactional
    public GuestDetails updateGuest(UUID eventId, UUID guestId, GuestRequest request) {
        eventFinder.findChangeableEvent(eventId);
        Guest guest = findGuest(eventId, guestId);
        CardType cardType = findCardType(eventId, request.cardTypeId());
        String phone = cleanPhone(request.phone());

        boolean phoneChanged = !phone.equals(guest.getPhone());
        if (phoneChanged && guestRepository.existsByEventIdAndPhone(eventId, phone)) {
            throw new ConflictException("A guest with this phone number is already on the list", "phone");
        }

        copyFormIntoGuest(request, guest, phone);
        return detailsOf(guest, cardType);
    }

    @Transactional
    public void deleteGuest(UUID eventId, UUID guestId) {
        eventFinder.findChangeableEvent(eventId);
        guestRepository.delete(findGuest(eventId, guestId));
    }

    private void copyFormIntoGuest(GuestRequest request, Guest guest, String cleanedPhone) {
        guest.setNameOnCard(request.nameOnCard().trim());
        guest.setPhone(cleanedPhone);
        guest.setCardTypeId(request.cardTypeId());
        guest.setGroupName(blankToNull(request.groupName()));
        guest.setNotes(blankToNull(request.notes()));
    }

    private String cleanPhone(String typedPhone) {
        return PhoneNumbers.toInternationalFormat(typedPhone, currentCompany.get().getCountryCode())
                .orElseThrow(() -> new InvalidInputException("Phone number is not valid", "phone"));
    }

    private Guest findGuest(UUID eventId, UUID guestId) {
        return guestRepository.findByIdAndEventId(guestId, eventId)
                .orElseThrow(() -> new NotFoundException("Guest not found"));
    }

    /** The card type must belong to this same event. */
    private CardType findCardType(UUID eventId, UUID cardTypeId) {
        return cardTypeRepository.findByIdAndEventId(cardTypeId, eventId)
                .orElseThrow(() -> new InvalidInputException("Please choose one of this event's card types", "cardTypeId"));
    }

    private Map<UUID, CardType> cardTypesOf(UUID eventId) {
        return cardTypeRepository.findByEventIdOrderBySortOrderAsc(eventId).stream()
                .collect(Collectors.toMap(CardType::getId, Function.identity()));
    }

    static GuestDetails detailsOf(Guest guest, CardType cardType) {
        return new GuestDetails(guest.getId(), guest.getNameOnCard(), guest.getPhone(),
                cardType.getId(), cardType.getName(), cardType.getSeats(),
                guest.getGroupName(), guest.getNotes());
    }

    static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }
}
