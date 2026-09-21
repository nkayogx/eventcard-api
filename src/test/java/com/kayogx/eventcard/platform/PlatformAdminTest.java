package com.kayogx.eventcard.platform;

import com.kayogx.eventcard.IntegrationTest;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PlatformAdminTest extends IntegrationTest {

    @Test
    void onlyThePlatformAdminCanSeeAllCompanies() throws Exception {
        TestCompany company = signUpNewCompany("Curious Co");

        get("/api/platform/companies", company.ownerToken()).andExpect(status().isForbidden());
        get("/api/platform/companies", null).andExpect(status().isUnauthorized());

        get("/api/platform/companies?search=Curious", logInAsPlatformAdmin())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companies[?(@.id == '" + company.companyId() + "')].name").value("Curious Co"));
    }

    @Test
    void aSuspendedCompanyIsLockedOutUntilReactivated() throws Exception {
        TestCompany company = signUpNewCompany("Suspended Co");
        String adminToken = logInAsPlatformAdmin();

        put("/api/platform/companies/" + company.companyId() + "/suspend", adminToken, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountStatus").value("SUSPENDED"));

        // Existing tokens stop working, and logging in again is refused
        get("/api/my-company", company.ownerToken())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Your company account is suspended. Please contact support."));
        post("/api/auth/login", null, Map.of("email", company.ownerEmail(), "password", PASSWORD))
                .andExpect(status().isForbidden());

        put("/api/platform/companies/" + company.companyId() + "/reactivate", adminToken, null)
                .andExpect(status().isOk());
        get("/api/my-company", company.ownerToken()).andExpect(status().isOk());
    }

    @Test
    void thePlatformAdminUnlocksMessageSending() throws Exception {
        TestCompany company = signUpNewCompany("Sender Co");
        String adminToken = logInAsPlatformAdmin();

        put("/api/platform/companies/" + company.companyId() + "/allow-sending", adminToken, null)
                .andExpect(status().isOk());
        get("/api/me", company.ownerToken())
                .andExpect(jsonPath("$.company.canSendMessages").value(true));

        put("/api/platform/companies/" + company.companyId() + "/block-sending", adminToken, null)
                .andExpect(status().isOk());
        get("/api/me", company.ownerToken())
                .andExpect(jsonPath("$.company.canSendMessages").value(false));
    }

    @Test
    void thePlatformAdminHasNoCompanyOfTheirOwn() throws Exception {
        String adminToken = logInAsPlatformAdmin();

        get("/api/me", adminToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("PLATFORM_ADMIN"))
                .andExpect(jsonPath("$.company").isEmpty());
        get("/api/my-company", adminToken).andExpect(status().isForbidden());
    }
}
