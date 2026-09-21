package com.kayogx.eventcard.user;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** The people who work in the logged-in user's company. */
@RestController
@RequestMapping("/api/my-company/staff")
public class StaffController {

    private final StaffService staffService;

    public StaffController(StaffService staffService) {
        this.staffService = staffService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER')")
    public List<StaffMember> listStaff() {
        return staffService.listStaff();
    }

    @PostMapping("/invitations")
    @PreAuthorize("hasRole('OWNER')")
    @ResponseStatus(HttpStatus.CREATED)
    public InvitationCreated inviteStaff(@Valid @RequestBody InviteStaffRequest request) {
        return staffService.inviteStaff(request);
    }

    @PutMapping("/{userId}")
    @PreAuthorize("hasRole('OWNER')")
    public StaffMember updateStaffMember(@PathVariable UUID userId, @RequestBody UpdateStaffRequest request) {
        return staffService.updateStaffMember(userId, request);
    }
}
