package com.kayogx.eventcard.controller;

import com.kayogx.eventcard.dto.AcceptInvitationRequest;
import com.kayogx.eventcard.dto.InvitationDetails;
import com.kayogx.eventcard.dto.LoginResponse;
import com.kayogx.eventcard.service.InvitationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** Public addresses used by someone who received an invitation link (not logged in yet). */
@RestController
@RequestMapping("/api/invitations/{code}")
public class InvitationController {

    private final InvitationService invitationService;

    public InvitationController(InvitationService invitationService) {
        this.invitationService = invitationService;
    }

    @GetMapping
    public InvitationDetails showInvitation(@PathVariable String code) {
        return invitationService.showInvitation(code);
    }

    @PostMapping("/accept")
    @ResponseStatus(HttpStatus.CREATED)
    public LoginResponse acceptInvitation(@PathVariable String code,
                                          @Valid @RequestBody AcceptInvitationRequest request) {
        return invitationService.acceptInvitation(code, request);
    }
}
