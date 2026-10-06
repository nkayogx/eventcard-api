package com.kayogx.eventcard.repository;

import com.kayogx.eventcard.model.Guest;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Guests are automatically limited to the logged-in user's company. */
public interface GuestRepository extends JpaRepository<Guest, UUID>, JpaSpecificationExecutor<Guest> {

    Optional<Guest> findByIdAndEventId(UUID id, UUID eventId);

    boolean existsByEventIdAndPhone(UUID eventId, String phone);

    boolean existsByCardTypeId(UUID cardTypeId);

    long countByEventId(UUID eventId);

    /**
     * Loads the guest and LOCKS the row until the transaction ends, so two gates
     * checking in the same card at the same moment cannot both use the same seats.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select guest from Guest guest where guest.id = :guestId and guest.eventId = :eventId")
    Optional<Guest> findByIdAndEventIdAndLockIt(UUID guestId, UUID eventId);

    /** Up to 20 guests of the event whose name or phone contains the search text. */
    @Query("""
            select guest from Guest guest
            where guest.eventId = :eventId
              and (lower(guest.nameOnCard) like :pattern or guest.phone like :pattern)
            order by guest.nameOnCard""")
    List<Guest> searchInEvent(UUID eventId, String pattern, Pageable firstResults);

    /** Arrivals of one event per card type. Each row: [cardTypeId, cards, cards with arrivals, people arrived]. */
    @Query("""
            select guest.cardTypeId, count(guest),
                   sum(case when guest.peopleArrived > 0 then 1 else 0 end), sum(guest.peopleArrived)
            from Guest guest where guest.eventId = :eventId group by guest.cardTypeId""")
    List<Object[]> countArrivalsPerCardType(UUID eventId);

    List<Guest> findByEventIdOrderByNameOnCardAsc(UUID eventId);

    void deleteAllByEventId(UUID eventId);

    @Query("select g.phone from Guest g where g.eventId = :eventId")
    Set<String> findPhonesOfEvent(UUID eventId);

    /** How many cards of each card type, for the given events. Each row: [eventId, cardTypeId, count]. */
    @Query("select g.eventId, g.cardTypeId, count(g) from Guest g where g.eventId in :eventIds group by g.eventId, g.cardTypeId")
    List<Object[]> countCardsPerCardType(Collection<UUID> eventIds);

    /** How many cards of each card type in each group of one event. Each row: [groupName, cardTypeId, count]. */
    @Query("select g.groupName, g.cardTypeId, count(g) from Guest g where g.eventId = :eventId group by g.groupName, g.cardTypeId")
    List<Object[]> countCardsPerGroupAndCardType(UUID eventId);

    Optional<Guest> findByInvitationCode(String invitationCode);

    List<Guest> findByInvitationCodeIsNull();

    /** RSVP answers of one event. Each row: [rsvpStatus, number of cards, number of people coming]. */
    @Query("select g.rsvpStatus, count(g), coalesce(sum(g.rsvpPeople), 0) from Guest g where g.eventId = :eventId group by g.rsvpStatus")
    List<Object[]> countRsvpAnswers(UUID eventId);

    @Query("select distinct g.groupName from Guest g where g.eventId = :eventId and g.groupName is not null order by g.groupName")
    List<String> findGroupNames(UUID eventId);
}
