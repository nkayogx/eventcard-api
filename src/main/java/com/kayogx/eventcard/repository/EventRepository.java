package com.kayogx.eventcard.repository;

import com.kayogx.eventcard.model.Event;
import com.kayogx.eventcard.model.EventStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

/** Events are automatically limited to the logged-in user's company. */
public interface EventRepository extends JpaRepository<Event, UUID>, JpaSpecificationExecutor<Event> {

    long countByStatus(EventStatus status);
}
