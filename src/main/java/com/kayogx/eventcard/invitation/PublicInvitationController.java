package com.kayogx.eventcard.invitation;

import com.kayogx.eventcard.invitation.InvitationForms.InvitationPage;
import com.kayogx.eventcard.invitation.InvitationForms.RsvpRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

/**
 * PUBLIC addresses for guests - no login needed.
 * The invitation code in the address is the key to one guest's invitation.
 */
@RestController
@RequestMapping("/api/public/invitations/{code}")
public class PublicInvitationController {

    private final PublicInvitationService invitationService;

    public PublicInvitationController(PublicInvitationService invitationService) {
        this.invitationService = invitationService;
    }

    @GetMapping
    public InvitationPage showInvitation(@PathVariable String code, HttpServletRequest request) {
        return invitationService.showInvitation(code, request.getRemoteAddr());
    }

    @GetMapping("/card.png")
    public ResponseEntity<byte[]> cardImage(@PathVariable String code, HttpServletRequest request) {
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                // Browsers may keep the card for a few minutes, but must ask again after that (the design may change)
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePrivate())
                .body(invitationService.cardImage(code, request.getRemoteAddr()));
    }

    @PostMapping("/rsvp")
    public InvitationPage answerRsvp(@PathVariable String code, @Valid @RequestBody RsvpRequest answer,
                                     HttpServletRequest request) {
        return invitationService.answerRsvp(code, request.getRemoteAddr(), answer);
    }
}
