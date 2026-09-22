package com.kayogx.eventcard;

import com.jayway.jsonpath.JsonPath;
import com.kayogx.eventcard.billing.PlanRepository;
import com.kayogx.eventcard.company.Company;
import com.kayogx.eventcard.company.CompanyRepository;
import com.kayogx.eventcard.company.DnsTxtLookup;
import com.kayogx.eventcard.tenant.AllCompaniesTransaction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base class for tests that start the whole application and call its API,
 * just like the React app would.
 *
 * It also offers small helpers such as {@link #signUpNewCompany(String)}
 * so each test can read like a short story.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTest {

    protected static final String PASSWORD = "Password123";
    protected static final String PLATFORM_ADMIN_EMAIL = "admin@test.local";
    protected static final String PLATFORM_ADMIN_PASSWORD = "AdminPassword1";

    @Autowired
    protected MockMvc api;

    @Autowired
    protected JsonMapper jsonMapper;

    /** A fake DNS, so tests don't depend on the internet. */
    @MockitoBean
    protected DnsTxtLookup dnsTxtLookup;

    @DynamicPropertySource
    static void chooseTestDatabase(DynamicPropertyRegistry settings) {
        TestDatabase.addSettings(settings);
    }

    /** A company created during a test, with its owner already logged in. */
    protected record TestCompany(UUID companyId, String slug, UUID ownerUserId, String ownerEmail, String ownerToken) {
    }

    // ---------- Helpers that call the API ----------

    @Autowired
    protected CompanyRepository companyRepository;

    @Autowired
    protected PlanRepository planRepository;

    @Autowired
    protected AllCompaniesTransaction allCompanies;

    /**
     * A new company on the PRO plan (paid for a month), so tests of other features
     * are not stopped by plan limits. Use {@link #signUpNewCompanyOnFreePlan} to test the limits.
     */
    protected TestCompany signUpNewCompany(String companyName) throws Exception {
        TestCompany company = signUpNewCompanyOnFreePlan(companyName);
        putOnPlan(company, "PRO", LocalDate.now().plusMonths(1));
        return company;
    }

    /** Puts a company on a plan paid until the given date (straight in the database, skipping payment). */
    protected void putOnPlan(TestCompany company, String planCode, LocalDate paidUntil) {
        allCompanies.run(() -> {
            Company saved = companyRepository.findById(company.companyId()).orElseThrow();
            saved.setPlanId(planRepository.findByCode(planCode).orElseThrow().getId());
            saved.setPlanPaidUntil(paidUntil);
            return null;
        });
    }

    protected TestCompany signUpNewCompanyOnFreePlan(String companyName) throws Exception {
        String ownerEmail = uniqueEmail("owner");
        String answer = post("/api/auth/signup-company", null, Map.of(
                "companyName", companyName,
                "fullName", "Owner of " + companyName,
                "email", ownerEmail,
                "phone", "+255712345678",
                "password", PASSWORD,
                "countryCode", "TZ",
                "timeZone", "Africa/Dar_es_Salaam"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return new TestCompany(
                UUID.fromString(JsonPath.read(answer, "$.me.company.id")),
                JsonPath.read(answer, "$.me.company.slug"),
                UUID.fromString(JsonPath.read(answer, "$.me.userId")),
                ownerEmail,
                JsonPath.read(answer, "$.token"));
    }

    /** Owner invites a new person with the given role; the person accepts. Returns the new person's login token. */
    protected String addStaffMember(TestCompany company, String role) throws Exception {
        String invitationCode = inviteStaff(company, uniqueEmail(role.toLowerCase()), role);
        String answer = post("/api/invitations/" + invitationCode + "/accept", null, Map.of(
                "fullName", "New " + role,
                "password", PASSWORD))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(answer, "$.token");
    }

    /** Returns the secret code from the invitation link. */
    protected String inviteStaff(TestCompany company, String email, String role) throws Exception {
        String answer = post("/api/my-company/staff/invitations", company.ownerToken(), Map.of(
                "email", email,
                "role", role))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String invitationLink = JsonPath.read(answer, "$.invitationLink");
        return invitationLink.substring(invitationLink.lastIndexOf('/') + 1);
    }

    protected String logInAs(String email, String password) throws Exception {
        String answer = post("/api/auth/login", null, Map.of("email", email, "password", password))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(answer, "$.token");
    }

    protected String logInAsPlatformAdmin() throws Exception {
        return logInAs(PLATFORM_ADMIN_EMAIL, PLATFORM_ADMIN_PASSWORD);
    }

    protected UUID userIdOf(String token) throws Exception {
        String answer = get("/api/me", token).andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(answer, "$.userId"));
    }

    /** Creates a draft event and returns the whole answer as JSON text. */
    protected String createEvent(String token, String eventName) throws Exception {
        return post("/api/events", token, Map.of(
                "name", eventName,
                "eventType", "WEDDING",
                "startsAt", "2026-12-12T16:00:00",
                "venueName", "Serena Hotel"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    protected String createEventAndGetId(String token) throws Exception {
        return JsonPath.read(createEvent(token, "Asha & Baraka's Wedding"), "$.id");
    }

    /** The id of the event's card type with the given name, e.g. "Double". */
    protected String cardTypeId(String token, String eventId, String cardTypeName) throws Exception {
        String event = get("/api/events/" + eventId, token).andReturn().getResponse().getContentAsString();
        List<String> ids = JsonPath.read(event, "$.cardTypes[?(@.name == '" + cardTypeName + "')].id");
        return ids.get(0);
    }

    protected ResultActions uploadFile(String address, String token, String fileName, byte[] content) throws Exception {
        var file = new MockMultipartFile("file", fileName, "application/octet-stream", content);
        return api.perform(MockMvcRequestBuilders.multipart(address).file(file)
                .header("Authorization", "Bearer " + token));
    }

    protected ResultActions delete(String address, String token) throws Exception {
        return api.perform(withToken(MockMvcRequestBuilders.delete(address), token));
    }

    // ---------- Plain HTTP helpers ----------

    protected ResultActions get(String address, String tokenOrNull) throws Exception {
        return api.perform(withToken(MockMvcRequestBuilders.get(address), tokenOrNull));
    }

    protected ResultActions post(String address, String tokenOrNull, Object body) throws Exception {
        return api.perform(withToken(MockMvcRequestBuilders.post(address), tokenOrNull)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(body)));
    }

    protected ResultActions postWithoutBody(String address, String tokenOrNull) throws Exception {
        return api.perform(withToken(MockMvcRequestBuilders.post(address), tokenOrNull));
    }

    protected ResultActions put(String address, String tokenOrNull, Object bodyOrNull) throws Exception {
        var request = withToken(MockMvcRequestBuilders.put(address), tokenOrNull);
        if (bodyOrNull != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(jsonMapper.writeValueAsString(bodyOrNull));
        }
        return api.perform(request);
    }

    protected static String uniqueEmail(String namePart) {
        return namePart + "-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
    }

    private static MockHttpServletRequestBuilder withToken(MockHttpServletRequestBuilder request, String tokenOrNull) {
        if (tokenOrNull != null) {
            request.header("Authorization", "Bearer " + tokenOrNull);
        }
        return request;
    }
}
