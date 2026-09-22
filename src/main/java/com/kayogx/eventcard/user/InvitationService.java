package com.kayogx.eventcard.user;

import com.kayogx.eventcard.auth.AuthService;
import com.kayogx.eventcard.auth.LoginResponse;
import com.kayogx.eventcard.billing.PlanLimits;
import com.kayogx.eventcard.common.ConflictException;
import com.kayogx.eventcard.common.NotAllowedException;
import com.kayogx.eventcard.common.NotFoundException;
import com.kayogx.eventcard.company.Company;
import com.kayogx.eventcard.company.CompanyRepository;
import com.kayogx.eventcard.tenant.AllCompaniesTransaction;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * The invited person's side of an invitation: see it, then accept it.
 * They are not logged in yet, so we look the invitation up across all companies
 * using its secret code.
 */
@Service
public class InvitationService {

    private final StaffInvitationRepository invitationRepository;
    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthService authService;
    private final AllCompaniesTransaction allCompaniesTransaction;
    private final PlanLimits planLimits;

    public InvitationService(StaffInvitationRepository invitationRepository,
                             UserRepository userRepository,
                             CompanyRepository companyRepository,
                             PasswordEncoder passwordEncoder,
                             AuthService authService,
                             AllCompaniesTransaction allCompaniesTransaction,
                             PlanLimits planLimits) {
        this.invitationRepository = invitationRepository;
        this.userRepository = userRepository;
        this.companyRepository = companyRepository;
        this.passwordEncoder = passwordEncoder;
        this.authService = authService;
        this.allCompaniesTransaction = allCompaniesTransaction;
        this.planLimits = planLimits;
    }

    public InvitationDetails showInvitation(String code) {
        return allCompaniesTransaction.run(() -> {
            StaffInvitation invitation = findUsableInvitation(code);
            Company company = findCompany(invitation);
            return new InvitationDetails(company.getName(), invitation.getEmail(), invitation.getRole());
        });
    }

    /** Creates the new user's account in the inviting company, then logs them in. */
    public LoginResponse acceptInvitation(String code, AcceptInvitationRequest request) {
        return allCompaniesTransaction.run(() -> {
            StaffInvitation invitation = findUsableInvitation(code);
            Company company = findCompany(invitation);

            if (company.isSuspended()) {
                throw new NotAllowedException("This company account is suspended. Please contact support.");
            }
            if (userRepository.existsByEmail(invitation.getEmail())) {
                throw new ConflictException("This email already has an account. Please log in instead.");
            }
            planLimits.checkCanAddStaff(company);

            User newUser = new User();
            newUser.setCompanyId(invitation.getCompanyId());
            newUser.setFullName(request.fullName().trim());
            newUser.setEmail(invitation.getEmail());
            newUser.setPhone(request.phone());
            newUser.setPasswordHash(passwordEncoder.encode(request.password()));
            newUser.setRole(invitation.getRole());
            userRepository.save(newUser);

            // Mark the invitation as used, so the same link cannot create a second account
            invitation.setAcceptedAt(Instant.now());

            return authService.loginResponseFor(newUser, company);
        });
    }

    private StaffInvitation findUsableInvitation(String code) {
        StaffInvitation invitation = invitationRepository.findByCode(code)
                .orElseThrow(() -> new NotFoundException("This invitation link is not valid"));
        if (invitation.isAlreadyUsed()) {
            throw new ConflictException("This invitation has already been used. Please log in instead.");
        }
        if (invitation.isExpired()) {
            throw new ConflictException("This invitation has expired. Please ask for a new one.");
        }
        return invitation;
    }

    private Company findCompany(StaffInvitation invitation) {
        return companyRepository.findById(invitation.getCompanyId())
                .orElseThrow(() -> new NotFoundException("This invitation link is not valid"));
    }
}
