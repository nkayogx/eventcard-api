package com.kayogx.eventcard.repository;

import com.kayogx.eventcard.model.CheckIn;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Check-ins are automatically limited to the logged-in user's company. */
public interface CheckInRepository extends JpaRepository<CheckIn, UUID> {

    List<CheckIn> findTop20ByEventIdOrderByCreatedAtDesc(UUID eventId);

    Optional<CheckIn> findByIdAndEventId(UUID id, UUID eventId);
}
