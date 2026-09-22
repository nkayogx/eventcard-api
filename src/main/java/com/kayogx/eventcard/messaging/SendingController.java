package com.kayogx.eventcard.messaging;

import com.kayogx.eventcard.messaging.SendingForms.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Sending an event's cards over WhatsApp and SMS. Owners and managers only. */
@RestController
@RequestMapping("/api/events/{eventId}")
@PreAuthorize("hasAnyRole('OWNER', 'MANAGER')")
public class SendingController {

    private final CardSendingService sendingService;

    public SendingController(CardSendingService sendingService) {
        this.sendingService = sendingService;
    }

    @GetMapping("/sending")
    public SendingOverview overview(@PathVariable UUID eventId) {
        return sendingService.overview(eventId);
    }

    @PostMapping("/sending/preview")
    public SendPreview preview(@PathVariable UUID eventId, @Valid @RequestBody SendRequest request) {
        return sendingService.preview(eventId, request);
    }

    @PostMapping("/sending")
    @ResponseStatus(HttpStatus.CREATED)
    public BatchDetails send(@PathVariable UUID eventId, @Valid @RequestBody SendRequest request) {
        return sendingService.send(eventId, request);
    }

    @PostMapping("/guests/{guestId}/send")
    @ResponseStatus(HttpStatus.CREATED)
    public MessageDetails sendToGuest(@PathVariable UUID eventId, @PathVariable UUID guestId,
                                      @Valid @RequestBody SendToGuestRequest request) {
        return sendingService.sendToGuest(eventId, guestId, request);
    }

    @GetMapping("/guests/{guestId}/messages")
    public List<MessageDetails> guestMessages(@PathVariable UUID eventId, @PathVariable UUID guestId) {
        return sendingService.guestMessages(eventId, guestId);
    }

    @PutMapping("/message-settings")
    public SendingOverview updateSettings(@PathVariable UUID eventId, @Valid @RequestBody MessageSettingsRequest request) {
        return sendingService.updateSettings(eventId, request);
    }
}
