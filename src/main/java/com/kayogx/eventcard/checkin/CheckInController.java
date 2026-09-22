package com.kayogx.eventcard.checkin;

import com.kayogx.eventcard.checkin.CheckInForms.*;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Check-in at the door. Check-in staff, managers and owners; only managers and owners can undo. */
@RestController
@RequestMapping("/api/events/{eventId}")
@PreAuthorize("hasAnyRole('OWNER', 'MANAGER', 'CHECK_IN_STAFF')")
public class CheckInController {

    private final CheckInService checkInService;

    public CheckInController(CheckInService checkInService) {
        this.checkInService = checkInService;
    }

    @PostMapping("/check-in/look-up")
    public LookUpResult lookUp(@PathVariable UUID eventId, @Valid @RequestBody LookUpRequest request) {
        return checkInService.lookUp(eventId, request.scanned());
    }

    @PostMapping("/check-in")
    public ArrivalGuest checkIn(@PathVariable UUID eventId, @Valid @RequestBody CheckInRequest request) {
        return checkInService.checkIn(eventId, request);
    }

    @GetMapping("/check-in/search")
    public List<ArrivalGuest> search(@PathVariable UUID eventId, @RequestParam(defaultValue = "") String q) {
        return checkInService.search(eventId, q);
    }

    @GetMapping("/check-in/summary")
    public ArrivalSummary summary(@PathVariable UUID eventId) {
        return checkInService.summary(eventId);
    }

    @PostMapping("/check-ins/{checkInId}/undo")
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER')")
    public ArrivalGuest undo(@PathVariable UUID eventId, @PathVariable UUID checkInId) {
        return checkInService.undo(eventId, checkInId);
    }
}
