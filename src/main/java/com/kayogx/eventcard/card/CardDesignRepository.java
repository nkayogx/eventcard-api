package com.kayogx.eventcard.card;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CardDesignRepository extends JpaRepository<CardDesign, UUID> {

    Optional<CardDesign> findByEventId(UUID eventId);

    void deleteByEventId(UUID eventId);
}
