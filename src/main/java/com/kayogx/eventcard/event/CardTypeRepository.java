package com.kayogx.eventcard.event;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CardTypeRepository extends JpaRepository<CardType, UUID> {

    List<CardType> findByEventIdOrderBySortOrderAsc(UUID eventId);

    List<CardType> findByEventIdIn(Collection<UUID> eventIds);

    Optional<CardType> findByIdAndEventId(UUID id, UUID eventId);

    boolean existsByEventIdAndNameIgnoreCase(UUID eventId, String name);

    long countByEventId(UUID eventId);

    void deleteAllByEventId(UUID eventId);
}
