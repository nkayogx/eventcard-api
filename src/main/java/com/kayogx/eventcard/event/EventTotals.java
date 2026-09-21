package com.kayogx.eventcard.event;

import com.kayogx.eventcard.event.EventResponses.CardTypeDetails;
import com.kayogx.eventcard.event.EventResponses.GroupTotals;
import com.kayogx.eventcard.event.EventResponses.RsvpTotals;
import com.kayogx.eventcard.guest.RsvpStatus;
import com.kayogx.eventcard.guest.GuestRepository;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Counts cards and seats on guest lists.
 * Seats are never stored: a guest's seats come from their card type (Double = 2 seats),
 * so we count the cards of each type and multiply.
 */
@Component
public class EventTotals {

    private final GuestRepository guestRepository;
    private final CardTypeRepository cardTypeRepository;

    public EventTotals(GuestRepository guestRepository, CardTypeRepository cardTypeRepository) {
        this.guestRepository = guestRepository;
        this.cardTypeRepository = cardTypeRepository;
    }

    /** Cards and seats for a whole event. */
    public record Totals(long cards, long seats) {
    }

    /** Totals for several events at once (used by the events list). */
    public Map<UUID, Totals> totalsForEvents(Collection<UUID> eventIds) {
        Map<UUID, Totals> totals = new HashMap<>();
        if (eventIds.isEmpty()) {
            return totals;
        }
        Map<UUID, Integer> seatsPerCardType = seatsPerCardType(cardTypeRepository.findByEventIdIn(eventIds));

        for (Object[] row : guestRepository.countCardsPerCardType(eventIds)) {
            UUID eventId = (UUID) row[0];
            UUID cardTypeId = (UUID) row[1];
            long cards = (Long) row[2];
            long seats = cards * seatsPerCardType.getOrDefault(cardTypeId, 0);

            Totals soFar = totals.getOrDefault(eventId, new Totals(0, 0));
            totals.put(eventId, new Totals(soFar.cards() + cards, soFar.seats() + seats));
        }
        return totals;
    }

    /** Each card type of the event, with how many cards and seats of that type. */
    public List<CardTypeDetails> cardTypesWithTotals(UUID eventId) {
        Map<UUID, Long> cardsPerCardType = new HashMap<>();
        for (Object[] row : guestRepository.countCardsPerGroupAndCardType(eventId)) {
            cardsPerCardType.merge((UUID) row[1], (Long) row[2], Long::sum);
        }

        return cardTypeRepository.findByEventIdOrderBySortOrderAsc(eventId).stream()
                .map(cardType -> {
                    long cards = cardsPerCardType.getOrDefault(cardType.getId(), 0L);
                    return new CardTypeDetails(cardType.getId(), cardType.getName(), cardType.getSeats(),
                            cards, cards * cardType.getSeats());
                })
                .toList();
    }

    /** Cards and seats per guest group, sorted by group name (guests without a group come last). */
    public List<GroupTotals> groupTotals(UUID eventId) {
        Map<UUID, Integer> seatsPerCardType = seatsPerCardType(cardTypeRepository.findByEventIdOrderBySortOrderAsc(eventId));
        // A TreeMap keeps the groups sorted by name
        Map<String, GroupTotals> totalsPerGroup = new TreeMap<>(Comparator.nullsLast(Comparator.naturalOrder()));

        for (Object[] row : guestRepository.countCardsPerGroupAndCardType(eventId)) {
            String groupName = (String) row[0];
            long cards = (Long) row[2];
            long seats = cards * seatsPerCardType.getOrDefault((UUID) row[1], 0);

            GroupTotals soFar = totalsPerGroup.getOrDefault(groupName, new GroupTotals(groupName, 0, 0));
            totalsPerGroup.put(groupName, new GroupTotals(groupName, soFar.cards() + cards, soFar.seats() + seats));
        }
        return new ArrayList<>(totalsPerGroup.values());
    }

    /** How the guests of one event have answered the RSVP. */
    public RsvpTotals rsvpTotals(UUID eventId) {
        long attendingCards = 0, attendingPeople = 0, notAttendingCards = 0, noReplyCards = 0;
        for (Object[] row : guestRepository.countRsvpAnswers(eventId)) {
            RsvpStatus status = row[0] == null ? RsvpStatus.NO_REPLY : (RsvpStatus) row[0];
            long cards = (Long) row[1];
            long people = ((Number) row[2]).longValue();
            switch (status) {
                case ATTENDING -> {
                    attendingCards += cards;
                    attendingPeople += people;
                }
                case NOT_ATTENDING -> notAttendingCards += cards;
                case NO_REPLY -> noReplyCards += cards;
            }
        }
        return new RsvpTotals(attendingCards, attendingPeople, notAttendingCards, noReplyCards);
    }

    private static Map<UUID, Integer> seatsPerCardType(List<CardType> cardTypes) {
        Map<UUID, Integer> seats = new HashMap<>();
        for (CardType cardType : cardTypes) {
            seats.put(cardType.getId(), cardType.getSeats());
        }
        return seats;
    }
}
