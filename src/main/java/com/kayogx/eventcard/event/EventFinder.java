package com.kayogx.eventcard.event;

import com.kayogx.eventcard.common.ConflictException;
import com.kayogx.eventcard.common.NotFoundException;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Finds an event of the logged-in user's company.
 * Events of other companies are invisible (see tenant/HibernateTenantSetup),
 * so asking for one simply gives "Event not found".
 */
@Component
public class EventFinder {

    private final EventRepository eventRepository;

    public EventFinder(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    public Event findEvent(UUID eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event not found"));
    }

    /** Same as {@link #findEvent}, but refuses finished and cancelled events, which can no longer be changed. */
    public Event findChangeableEvent(UUID eventId) {
        Event event = findEvent(eventId);
        if (event.getStatus().isReadOnly()) {
            throw new ConflictException("This event is " + event.getStatus().name().toLowerCase()
                    + " and can no longer be changed");
        }
        return event;
    }
}
