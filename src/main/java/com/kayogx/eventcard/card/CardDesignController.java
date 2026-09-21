package com.kayogx.eventcard.card;

import com.kayogx.eventcard.card.CardDesignForms.CardDesignDetails;
import com.kayogx.eventcard.card.CardDesignForms.SaveCardDesignRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * The card design of one event, and card images for the vendor.
 * Everyone in the company may VIEW; owners and managers may CHANGE.
 */
@RestController
@RequestMapping("/api/events/{eventId}")
@PreAuthorize("hasAnyRole('OWNER', 'MANAGER', 'CHECK_IN_STAFF')")
public class CardDesignController {

    private final CardDesignService cardDesignService;

    public CardDesignController(CardDesignService cardDesignService) {
        this.cardDesignService = cardDesignService;
    }

    @GetMapping("/card-design")
    public CardDesignDetails getDesign(@PathVariable UUID eventId) {
        return cardDesignService.getDesign(eventId);
    }

    @PutMapping("/card-design")
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER')")
    public CardDesignDetails saveDesign(@PathVariable UUID eventId, @Valid @RequestBody SaveCardDesignRequest request) {
        return cardDesignService.saveDesign(eventId, request);
    }

    @PostMapping("/card-design/background")
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER')")
    public CardDesignDetails uploadArtwork(@PathVariable UUID eventId, @RequestParam("file") MultipartFile file) {
        return cardDesignService.uploadArtwork(eventId, file);
    }

    @PostMapping("/card-types/{cardTypeId}/background")
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER')")
    public CardDesignDetails uploadCardTypeArtwork(@PathVariable UUID eventId, @PathVariable UUID cardTypeId,
                                                   @RequestParam("file") MultipartFile file) {
        return cardDesignService.uploadCardTypeArtwork(eventId, cardTypeId, file);
    }

    @DeleteMapping("/card-types/{cardTypeId}/background")
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER')")
    public CardDesignDetails removeCardTypeArtwork(@PathVariable UUID eventId, @PathVariable UUID cardTypeId) {
        return cardDesignService.removeCardTypeArtwork(eventId, cardTypeId);
    }

    /** A sample card. {@code template} previews a template without saving it (for the style picker). */
    @GetMapping("/card-design/preview.png")
    public ResponseEntity<byte[]> previewCard(@PathVariable UUID eventId,
                                              @RequestParam(required = false) UUID cardTypeId,
                                              @RequestParam(required = false) CardTemplate template) {
        return png(cardDesignService.previewCard(eventId, cardTypeId, template));
    }

    @GetMapping("/guests/{guestId}/card.png")
    public ResponseEntity<byte[]> guestCard(@PathVariable UUID eventId, @PathVariable UUID guestId) {
        return png(cardDesignService.guestCard(eventId, guestId));
    }

    static ResponseEntity<byte[]> png(byte[] image) {
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(image);
    }
}
