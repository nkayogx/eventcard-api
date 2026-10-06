package com.kayogx.eventcard.tenant;

import com.kayogx.eventcard.IntegrationTest;
import com.kayogx.eventcard.repository.UserRepository;
import com.kayogx.eventcard.service.AllCompaniesTransaction;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The most important tests of all: one company must NEVER see or change
 * another company's data.
 */
class TenantIsolationTest extends IntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AllCompaniesTransaction allCompaniesTransaction;

    @Test
    void aCompanyOnlySeesItsOwnStaff() throws Exception {
        TestCompany companyA = signUpNewCompany("Company A");
        TestCompany companyB = signUpNewCompany("Company B");
        addStaffMember(companyB, "MANAGER");

        get("/api/my-company/staff", companyA.ownerToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[*].email", hasItem(companyA.ownerEmail())))
                .andExpect(jsonPath("$[*].email", not(hasItem(companyB.ownerEmail()))));
    }

    @Test
    void aCompanyCannotChangeAnotherCompanysStaff() throws Exception {
        TestCompany companyA = signUpNewCompany("Company A");
        TestCompany companyB = signUpNewCompany("Company B");
        UUID managerOfB = userIdOf(addStaffMember(companyB, "MANAGER"));

        // Company A's owner tries to deactivate Company B's manager: it is "not found" for them
        put("/api/my-company/staff/" + managerOfB, companyA.ownerToken(), Map.of("active", false))
                .andExpect(status().isNotFound());

        // ...and the manager of B is untouched
        get("/api/my-company/staff", companyB.ownerToken())
                .andExpect(jsonPath("$[?(@.id == '" + managerOfB + "')].active").value(true));
    }

    @Test
    void myCompanyAlwaysMeansTheLoggedInUsersCompany() throws Exception {
        TestCompany companyA = signUpNewCompany("Company A");
        TestCompany companyB = signUpNewCompany("Company B");

        get("/api/my-company", companyA.ownerToken())
                .andExpect(jsonPath("$.id").value(companyA.companyId().toString()));
        get("/api/my-company", companyB.ownerToken())
                .andExpect(jsonPath("$.id").value(companyB.companyId().toString()));
    }

    @Test
    void codeThatForgetsToChooseACompanySeesNothing() throws Exception {
        signUpNewCompany("Company A");

        // No company has been set for this code, so the safety net hides every user...
        assertThat(userRepository.findAll()).isEmpty();

        // ...while "all companies" mode (used by login, signup, admin) sees them
        assertThat(allCompaniesTransaction.run(() -> userRepository.findAll())).isNotEmpty();
    }

    @Test
    void invitedStaffJoinTheCompanyThatInvitedThem() throws Exception {
        TestCompany companyA = signUpNewCompany("Company A");
        String managerToken = addStaffMember(companyA, "MANAGER");

        get("/api/me", managerToken)
                .andExpect(jsonPath("$.company.id").value(companyA.companyId().toString()));
        get("/api/my-company/staff", managerToken)
                .andExpect(jsonPath("$.length()").value(2));
    }
}
