package com.kayogx.eventcard.event;

import com.kayogx.eventcard.common.ConflictException;
import com.kayogx.eventcard.common.NotFoundException;
import com.kayogx.eventcard.event.EventResponses.EventDetails;
import com.kayogx.eventcard.guest.GuestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Adding, changing and removing an event's card types (Single, Double, VIP...).
 * Every change answers with the whole event, so the page can refresh its totals.
 */
@Service
public class CardTypeService {

    private final CardTypeRepository cardTypeRepository;
    private final GuestRepository guestRepository;
    private final EventFinder eventFinder;
    private final EventService eventService;

    public CardTypeService(CardTypeRepository cardTypeRepository,
                           GuestRepository guestRepository,
                           EventFinder eventFinder,
                           EventService eventService) {
        this.cardTypeRepository = cardTypeRepository;
        this.guestRepository = guestRepository;
        this.eventFinder = eventFinder;
        this.eventService = eventService;
    }

    @Transactional
    public EventDetails addCardType(UUID eventId, CardTypeRequest request) {
        Event event = eventFinder.findChangeableEvent(eventId);
        String name = request.name().trim();
        if (cardTypeRepository.existsByEventIdAndNameIgnoreCase(eventId, name)) {
            throw new ConflictException("This event already has a card type called " + name, "name");
        }

        CardType cardType = new CardType();
        cardType.setCompanyId(event.getCompanyId());
        cardType.setEventId(eventId);
        cardType.setName(name);
        cardType.setSeats(request.seats());
        cardType.setSortOrder((int) cardTypeRepository.countByEventId(eventId) + 1);  // new types go last
        cardTypeRepository.save(cardType);
        return eventService.detailsOf(event);
    }

    @Transactional
    public EventDetails updateCardType(UUID eventId, UUID cardTypeId, CardTypeRequest request) {
        Event event = eventFinder.findChangeableEvent(eventId);
        CardType cardType = findCardType(eventId, cardTypeId);
        String newName = request.name().trim();

        boolean nameChanged = !newName.equalsIgnoreCase(cardType.getName());
        if (nameChanged && cardTypeRepository.existsByEventIdAndNameIgnoreCase(eventId, newName)) {
            throw new ConflictException("This event already has a card type called " + newName, "name");
        }

        cardType.setName(newName);
        cardType.setSeats(request.seats());
        return eventService.detailsOf(event);
    }

    @Transactional
    public EventDetails deleteCardType(UUID eventId, UUID cardTypeId) {
        Event event = eventFinder.findChangeableEvent(eventId);
        CardType cardType = findCardType(eventId, cardTypeId);

        if (guestRepository.existsByCardTypeId(cardTypeId)) {
            throw new ConflictException("Some guests have this card type. Give them another card type first.");
        }
        if (cardTypeRepository.countByEventId(eventId) <= 1) {
            throw new ConflictException("An event needs at least one card type.");
        }

        cardTypeRepository.delete(cardType);
        return eventService.detailsOf(event);
    }

    private CardType findCardType(UUID eventId, UUID cardTypeId) {
        return cardTypeRepository.findByIdAndEventId(cardTypeId, eventId)
                .orElseThrow(() -> new NotFoundException("Card type not found"));
    }
}
