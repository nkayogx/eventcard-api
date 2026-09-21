package com.kayogx.eventcard.auth;

import com.kayogx.eventcard.IntegrationTest;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SignupAndLoginTest extends IntegrationTest {

    @Test
    void signingUpCreatesTheCompanyAndMakesYouItsOwner() throws Exception {
        TestCompany company = signUpNewCompany("Kayo Events");

        get("/api/me", company.ownerToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("OWNER"))
                .andExpect(jsonPath("$.email").value(company.ownerEmail()))
                .andExpect(jsonPath("$.company.name").value("Kayo Events"))
                .andExpect(jsonPath("$.company.accountStatus").value("ACTIVE"))
                // New companies cannot send messages until the platform admin verifies them
                .andExpect(jsonPath("$.company.canSendMessages").value(false));
    }

    @Test
    void twoCompaniesWithTheSameNameGetDifferentShortNames() throws Exception {
        String name = "Same Name " + UUID.randomUUID().toString().substring(0, 6);

        TestCompany first = signUpNewCompany(name);
        TestCompany second = signUpNewCompany(name);

        assertThat(second.slug()).isEqualTo(first.slug() + "-2");
    }

    @Test
    void anEmailCanOnlyBeRegisteredOnce() throws Exception {
        TestCompany existing = signUpNewCompany("First Company");

        Map<String, Object> form = validSignupForm();
        form.put("email", existing.ownerEmail().toUpperCase());  // same email, different letter case

        post("/api/auth/signup-company", null, form)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.field").value("email"));
    }

    @Test
    void signupFormMistakesAreExplainedPerField() throws Exception {
        Map<String, Object> shortPassword = validSignupForm();
        shortPassword.put("password", "short");
        post("/api/auth/signup-company", null, shortPassword)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field").value("password"))
                .andExpect(jsonPath("$.message").value("Password must be at least 8 characters"));

        Map<String, Object> unknownTimeZone = validSignupForm();
        unknownTimeZone.put("timeZone", "Mars/Olympus");
        post("/api/auth/signup-company", null, unknownTimeZone)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field").value("timeZone"));
    }

    @Test
    void loginWorksWithTheRightPasswordOnly() throws Exception {
        TestCompany company = signUpNewCompany("Login Test Co");

        String token = logInAs(company.ownerEmail(), PASSWORD);
        get("/api/me", token).andExpect(status().isOk());

        post("/api/auth/login", null, Map.of("email", company.ownerEmail(), "password", "WrongPassword"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Wrong email or password"));
    }

    @Test
    void protectedPagesNeedAValidToken() throws Exception {
        get("/api/me", null).andExpect(status().isUnauthorized());
        get("/api/me", "not-a-real-token").andExpect(status().isUnauthorized());
    }

    @Test
    void theReactWebsiteIsAllowedToCallTheApiFromTheBrowser() throws Exception {
        api.perform(options("/api/me")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }

    private static Map<String, Object> validSignupForm() {
        Map<String, Object> form = new HashMap<>();
        form.put("companyName", "Some Company");
        form.put("fullName", "Some Person");
        form.put("email", uniqueEmail("someone"));
        form.put("phone", "+255712345678");
        form.put("password", PASSWORD);
        form.put("countryCode", "TZ");
        form.put("timeZone", "Africa/Dar_es_Salaam");
        return form;
    }
}
