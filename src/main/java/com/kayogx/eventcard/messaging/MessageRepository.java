package com.kayogx.eventcard.messaging;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Messages are automatically limited to the logged-in user's company (the worker works "as all companies"). */
public interface MessageRepository extends JpaRepository<Message, UUID> {

    /** Messages ready to be sent now, oldest first. */
    List<Message> findTop20ByStatusAndNextAttemptAtLessThanEqualOrderByQueuedAtAsc(MessageStatus status, Instant now);

    List<Message> findByEventIdOrderByQueuedAtAsc(UUID eventId);

    List<Message> findByGuestIdInOrderByQueuedAtAsc(Collection<UUID> guestIds);

    List<Message> findByEventIdAndGuestIdOrderByQueuedAtDesc(UUID eventId, UUID guestId);

    List<Message> findTop20ByEventIdAndStatusOrderByQueuedAtDesc(UUID eventId, MessageStatus status);

    Optional<Message> findFirstByProviderMessageId(String providerMessageId);

    /** How many messages of the event are in each status. Each row: [status, count]. */
    @Query("select m.status, count(m) from Message m where m.eventId = :eventId group by m.status")
    List<Object[]> countByStatus(UUID eventId);
}
