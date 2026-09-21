package com.kayogx.eventcard.user;

import com.kayogx.eventcard.IntegrationTest;
import com.kayogx.eventcard.tenant.AllCompaniesTransaction;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StaffAndInvitationTest extends IntegrationTest {

    @Autowired
    private StaffInvitationRepository invitationRepository;

    @Autowired
    private AllCompaniesTransaction allCompaniesTransaction;

    @Test
    void anInvitedPersonCanSeeTheInvitationAndJoin() throws Exception {
        TestCompany company = signUpNewCompany("Invite Co");
        String email = uniqueEmail("manager");
        String code = inviteStaff(company, email, "MANAGER");

        get("/api/invitations/" + code, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyName").value("Invite Co"))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.role").value("MANAGER"));

        post("/api/invitations/" + code + "/accept", null, Map.of("fullName", "Maria Manager", "password", PASSWORD))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.me.role").value("MANAGER"));

        // The new manager can now log in normally
        logInAs(email, PASSWORD);
    }

    @Test
    void anInvitationLinkWorksOnlyOnce() throws Exception {
        TestCompany company = signUpNewCompany("Invite Co");
        String code = inviteStaff(company, uniqueEmail("staff"), "CHECK_IN_STAFF");
        Map<String, String> form = Map.of("fullName", "Door Person", "password", PASSWORD);

        post("/api/invitations/" + code + "/accept", null, form).andExpect(status().isCreated());
        post("/api/invitations/" + code + "/accept", null, form).andExpect(status().isConflict());
    }

    @Test
    void anExpiredInvitationCannotBeUsed() throws Exception {
        TestCompany company = signUpNewCompany("Invite Co");
        String code = inviteStaff(company, uniqueEmail("late"), "MANAGER");
        makeInvitationExpired(code);

        post("/api/invitations/" + code + "/accept", null, Map.of("fullName", "Late Person", "password", PASSWORD))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("This invitation has expired. Please ask for a new one."));
    }

    @Test
    void youCannotInviteSomeoneWhoAlreadyHasAnAccount() throws Exception {
        TestCompany companyA = signUpNewCompany("Company A");
        TestCompany companyB = signUpNewCompany("Company B");

        // Emails are unique across the whole platform, not just inside one company
        post("/api/my-company/staff/invitations", companyA.ownerToken(),
                Map.of("email", companyB.ownerEmail(), "role", "MANAGER"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.field").value("email"));
    }

    @Test
    void nobodyCanBeInvitedAsPlatformAdmin() throws Exception {
        TestCompany company = signUpNewCompany("Sneaky Co");

        post("/api/my-company/staff/invitations", company.ownerToken(),
                Map.of("email", uniqueEmail("sneaky"), "role", "PLATFORM_ADMIN"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void onlyTheOwnerCanInviteAndChangeStaff() throws Exception {
        TestCompany company = signUpNewCompany("Roles Co");
        String managerToken = addStaffMember(company, "MANAGER");
        String checkInToken = addStaffMember(company, "CHECK_IN_STAFF");

        post("/api/my-company/staff/invitations", managerToken, Map.of("email", uniqueEmail("x"), "role", "MANAGER"))
                .andExpect(status().isForbidden());
        put("/api/my-company/staff/" + company.ownerUserId(), managerToken, Map.of("active", false))
                .andExpect(status().isForbidden());

        // Managers may see the staff list, check-in staff may not
        get("/api/my-company/staff", managerToken).andExpect(status().isOk());
        get("/api/my-company/staff", checkInToken).andExpect(status().isForbidden());
    }

    @Test
    void theOwnerCanDeactivateStaffAndTheyAreLockedOutAtOnce() throws Exception {
        TestCompany company = signUpNewCompany("Lockout Co");
        String managerToken = addStaffMember(company, "MANAGER");
        UUID managerId = userIdOf(managerToken);

        put("/api/my-company/staff/" + managerId, company.ownerToken(), Map.of("active", false))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        // The manager's existing token stops working immediately
        get("/api/me", managerToken).andExpect(status().isUnauthorized());
    }

    @Test
    void theOwnerCanChangeSomeonesRole() throws Exception {
        TestCompany company = signUpNewCompany("Promote Co");
        UUID managerId = userIdOf(addStaffMember(company, "MANAGER"));

        put("/api/my-company/staff/" + managerId, company.ownerToken(), Map.of("role", "OWNER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("OWNER"));
    }

    @Test
    void youCannotDemoteOrDeactivateYourself() throws Exception {
        TestCompany company = signUpNewCompany("Self Co");

        put("/api/my-company/staff/" + company.ownerUserId(), company.ownerToken(), Map.of("role", "MANAGER"))
                .andExpect(status().isForbidden());
        put("/api/my-company/staff/" + company.ownerUserId(), company.ownerToken(), Map.of("active", false))
                .andExpect(status().isForbidden());
    }

    private void makeInvitationExpired(String code) {
        allCompaniesTransaction.run(() -> {
            StaffInvitation invitation = invitationRepository.findByCode(code).orElseThrow();
            invitation.setExpiresAt(Instant.now().minusSeconds(60));
            return null;
        });
    }
}
