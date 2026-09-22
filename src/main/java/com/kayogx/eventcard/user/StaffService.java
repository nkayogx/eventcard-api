package com.kayogx.eventcard.user;

import com.kayogx.eventcard.auth.LoggedInUser;
import com.kayogx.eventcard.billing.PlanLimits;
import com.kayogx.eventcard.company.CurrentCompany;
import com.kayogx.eventcard.common.ConflictException;
import com.kayogx.eventcard.common.EmailAddresses;
import com.kayogx.eventcard.common.InvalidInputException;
import com.kayogx.eventcard.common.NotAllowedException;
import com.kayogx.eventcard.common.NotFoundException;
import com.kayogx.eventcard.common.RandomCodes;
import com.kayogx.eventcard.tenant.AllCompaniesTransaction;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Managing the people who work in a company: list them, invite new ones,
 * change roles and deactivate accounts.
 *
 * All users here are automatically limited to the logged-in user's company
 * (see tenant/HibernateTenantSetup), so another company's staff is never visible.
 */
@Service
public class StaffService {

    private static final Duration INVITATION_VALID_FOR = Duration.ofDays(7);

    private final UserRepository userRepository;
    private final StaffInvitationRepository invitationRepository;
    private final AllCompaniesTransaction allCompaniesTransaction;
    private final String frontendUrl;
    private final PlanLimits planLimits;
    private final CurrentCompany currentCompany;

    public StaffService(UserRepository userRepository,
                        StaffInvitationRepository invitationRepository,
                        AllCompaniesTransaction allCompaniesTransaction,
                        @Value("${app.frontend-url}") String frontendUrl,
                        PlanLimits planLimits,
                        CurrentCompany currentCompany) {
        this.userRepository = userRepository;
        this.invitationRepository = invitationRepository;
        this.allCompaniesTransaction = allCompaniesTransaction;
        this.frontendUrl = frontendUrl;
        this.planLimits = planLimits;
        this.currentCompany = currentCompany;
    }

    @Transactional(readOnly = true)
    public List<StaffMember> listStaff() {
        return userRepository.findAllByOrderByFullNameAsc().stream()
                .map(StaffMember::from)
                .toList();
    }

    @Transactional
    public InvitationCreated inviteStaff(InviteStaffRequest request) {
        if (request.role() == UserRole.PLATFORM_ADMIN) {
            throw new InvalidInputException("You can only invite owners, managers or check-in staff", "role");
        }
        String email = EmailAddresses.normalize(request.email());

        // Emails are unique across the WHOLE platform, so we check every company, not just ours
        boolean emailAlreadyUsed = allCompaniesTransaction.run(() -> userRepository.existsByEmail(email));
        if (emailAlreadyUsed) {
            throw new ConflictException("This email already has an account", "email");
        }

        planLimits.checkCanAddStaff(currentCompany.get());

        LoggedInUser owner = LoggedInUser.current();
        StaffInvitation invitation = new StaffInvitation();
        invitation.setCompanyId(owner.companyId());
        invitation.setEmail(email);
        invitation.setRole(request.role());
        invitation.setCode(RandomCodes.newCode());
        invitation.setExpiresAt(Instant.now().plus(INVITATION_VALID_FOR));
        invitation.setInvitedByUserId(owner.userId());
        invitationRepository.save(invitation);

        String invitationLink = frontendUrl + "/accept-invitation/" + invitation.getCode();
        return new InvitationCreated(email, invitation.getRole(), invitationLink, invitation.getExpiresAt());
    }

    @Transactional
    public StaffMember updateStaffMember(UUID userId, UpdateStaffRequest request) {
        if (userId.equals(LoggedInUser.current().userId())) {
            throw new NotAllowedException("You cannot change your own role or deactivate yourself");
        }
        if (request.role() == UserRole.PLATFORM_ADMIN) {
            throw new InvalidInputException("You can only choose owner, manager or check-in staff", "role");
        }

        // A user from another company is simply "not found" here, thanks to the company filter
        User staffMember = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Staff member not found"));

        UserRole newRole = request.role() != null ? request.role() : staffMember.getRole();
        boolean newActive = request.active() != null ? request.active() : staffMember.isActive();

        makeSureCompanyKeepsAnOwner(staffMember, newRole, newActive);
        boolean reactivating = !staffMember.isActive() && newActive;
        if (reactivating) {
            planLimits.checkCanAddStaff(currentCompany.get());
        }

        staffMember.setRole(newRole);
        staffMember.setActive(newActive);
        return StaffMember.from(staffMember);
    }

    /** A company must always have at least one active owner, or nobody could manage it. */
    private void makeSureCompanyKeepsAnOwner(User staffMember, UserRole newRole, boolean newActive) {
        boolean isActiveOwnerNow = staffMember.getRole() == UserRole.OWNER && staffMember.isActive();
        boolean willStillBeActiveOwner = newRole == UserRole.OWNER && newActive;
        if (isActiveOwnerNow && !willStillBeActiveOwner
                && userRepository.countByRoleAndActiveTrue(UserRole.OWNER) <= 1) {
            throw new ConflictException("The company must keep at least one active owner");
        }
    }
}
