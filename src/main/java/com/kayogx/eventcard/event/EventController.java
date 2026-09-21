package com.kayogx.eventcard.event;

import com.kayogx.eventcard.event.EventResponses.EventDetails;
import com.kayogx.eventcard.event.EventResponses.EventPage;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Events of the logged-in user's company.
 * Everyone in the company may VIEW; owners and managers may CHANGE; only owners may DELETE.
 */
@RestController
@RequestMapping("/api/events")
@PreAuthorize("hasAnyRole('OWNER', 'MANAGER', 'CHECK_IN_STAFF')")
public class EventController {

    private final EventService eventService;
    private final CardTypeService cardTypeService;

    public EventController(EventService eventService, CardTypeService cardTypeService) {
        this.eventService = eventService;
        this.cardTypeService = cardTypeService;
    }

    @GetMapping
    public EventPage listEvents(@RequestParam(required = false) EventStatus status,
                                @RequestParam(required = false) String search,
                                @RequestParam(defaultValue = "0") int page) {
        return eventService.listEvents(status, search, page);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER')")
    @ResponseStatus(HttpStatus.CREATED)
    public EventDetails createEvent(@Valid @RequestBody EventRequest request) {
        return eventService.createEvent(request);
    }

    @GetMapping("/{eventId}")
    public EventDetails getEvent(@PathVariable UUID eventId) {
        return eventService.getEvent(eventId);
    }

    @PutMapping("/{eventId}")
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER')")
    public EventDetails updateEvent(@PathVariable UUID eventId, @Valid @RequestBody EventRequest request) {
        return eventService.updateEvent(eventId, request);
    }

    @PutMapping("/{eventId}/status")
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER')")
    public EventDetails changeStatus(@PathVariable UUID eventId, @Valid @RequestBody ChangeStatusRequest request) {
        return eventService.changeStatus(eventId, request.status());
    }

    @DeleteMapping("/{eventId}")
    @PreAuthorize("hasRole('OWNER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteEvent(@PathVariable UUID eventId) {
        eventService.deleteEvent(eventId);
    }

    // ---------- Card types ----------

    @PostMapping("/{eventId}/card-types")
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER')")
    public EventDetails addCardType(@PathVariable UUID eventId, @Valid @RequestBody CardTypeRequest request) {
        return cardTypeService.addCardType(eventId, request);
    }

    @PutMapping("/{eventId}/card-types/{cardTypeId}")
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER')")
    public EventDetails updateCardType(@PathVariable UUID eventId, @PathVariable UUID cardTypeId,
                                       @Valid @RequestBody CardTypeRequest request) {
        return cardTypeService.updateCardType(eventId, cardTypeId, request);
    }

    @DeleteMapping("/{eventId}/card-types/{cardTypeId}")
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER')")
    public EventDetails deleteCardType(@PathVariable UUID eventId, @PathVariable UUID cardTypeId) {
        return cardTypeService.deleteCardType(eventId, cardTypeId);
    }
}
