package com.kayogx.eventcard.guest;

import com.kayogx.eventcard.guest.GuestResponses.GuestDetails;
import com.kayogx.eventcard.guest.GuestResponses.GuestPage;
import com.kayogx.eventcard.guest.ImportResponses.ImportPreview;
import com.kayogx.eventcard.guest.ImportResponses.ImportResult;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * The guest list of one event.
 * Everyone in the company may VIEW it; owners and managers may CHANGE it.
 */
@RestController
@RequestMapping("/api/events/{eventId}/guests")
@PreAuthorize("hasAnyRole('OWNER', 'MANAGER', 'CHECK_IN_STAFF')")
public class GuestController {

    private final GuestService guestService;
    private final GuestImportService guestImportService;
    private final GuestListTemplate guestListTemplate;

    public GuestController(GuestService guestService,
                           GuestImportService guestImportService,
                           GuestListTemplate guestListTemplate) {
        this.guestService = guestService;
        this.guestImportService = guestImportService;
        this.guestListTemplate = guestListTemplate;
    }

    @GetMapping
    public GuestPage listGuests(@PathVariable UUID eventId,
                                @RequestParam(required = false) String search,
                                @RequestParam(required = false) UUID cardTypeId,
                                @RequestParam(required = false) String group,
                                @RequestParam(required = false) RsvpStatus rsvp,
                                @RequestParam(defaultValue = "0") int page) {
        return guestService.listGuests(eventId, search, cardTypeId, group, rsvp, page);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER')")
    @ResponseStatus(HttpStatus.CREATED)
    public GuestDetails addGuest(@PathVariable UUID eventId, @Valid @RequestBody GuestRequest request) {
        return guestService.addGuest(eventId, request);
    }

    @PutMapping("/{guestId}")
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER')")
    public GuestDetails updateGuest(@PathVariable UUID eventId, @PathVariable UUID guestId,
                                    @Valid @RequestBody GuestRequest request) {
        return guestService.updateGuest(eventId, guestId, request);
    }

    @DeleteMapping("/{guestId}")
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteGuest(@PathVariable UUID eventId, @PathVariable UUID guestId) {
        guestService.deleteGuest(eventId, guestId);
    }

    // ---------- Uploading a guest list ----------

    /** Step 1: read the file and show what would happen. Nothing is saved. */
    @PostMapping("/import/check")
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER')")
    public ImportPreview checkImport(@PathVariable UUID eventId, @RequestParam("file") MultipartFile file) {
        return guestImportService.check(eventId, file);
    }

    /** Step 2: save the good rows of the same file. */
    @PostMapping("/import")
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER')")
    public ImportResult importGuests(@PathVariable UUID eventId, @RequestParam("file") MultipartFile file) {
        return guestImportService.importGuests(eventId, file);
    }

    @GetMapping("/import/template")
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER')")
    public ResponseEntity<byte[]> downloadTemplate(@PathVariable UUID eventId) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"guest-list-template.xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(guestListTemplate.createFor(eventId));
    }
}
