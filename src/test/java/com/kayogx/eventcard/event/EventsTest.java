package com.kayogx.eventcard.event;

import com.kayogx.eventcard.IntegrationTest;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EventsTest extends IntegrationTest {

    @Test
    void aNewEventIsADraftWithSingleAndDoubleCards() throws Exception {
        TestCompany company = signUpNewCompany("Events Co");
        String eventId = createEventAndGetId(company.ownerToken());

        get("/api/events/" + eventId, company.ownerToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.timeZone").value("Africa/Dar_es_Salaam"))
                .andExpect(jsonPath("$.cardTypes[0].name").value("Single"))
                .andExpect(jsonPath("$.cardTypes[0].seats").value(1))
                .andExpect(jsonPath("$.cardTypes[1].name").value("Double"))
                .andExpect(jsonPath("$.cardTypes[1].seats").value(2))
                .andExpect(jsonPath("$.allowedNextStatuses[0]").value("ACTIVE"));
    }

    @Test
    void eventFormMistakesAreExplained() throws Exception {
        TestCompany company = signUpNewCompany("Events Co");

        Map<String, Object> endsBeforeStart = eventForm();
        endsBeforeStart.put("endsAt", "2026-12-12T15:00:00");
        post("/api/events", company.ownerToken(), endsBeforeStart)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field").value("endsAt"));

        Map<String, Object> badMapLink = eventForm();
        badMapLink.put("mapLink", "serena hotel");
        post("/api/events", company.ownerToken(), badMapLink)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field").value("mapLink"));

        Map<String, Object> contactPhone = eventForm();
        contactPhone.put("contactPhone", "0754 111 222");
        post("/api/events", company.ownerToken(), contactPhone)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contactPhone").value("+255754111222"));
    }

    @Test
    void theEventsListShowsCardAndSeatTotals() throws Exception {
        TestCompany company = signUpNewCompany("Totals Co");
        String token = company.ownerToken();
        String eventId = createEventAndGetId(token);
        String doubleCard = cardTypeId(token, eventId, "Double");
        String singleCard = cardTypeId(token, eventId, "Single");

        addGuest(token, eventId, "Mr & Mrs Juma", "0712000001", doubleCard);
        addGuest(token, eventId, "Mr & Mrs Salim", "0712000002", doubleCard);
        addGuest(token, eventId, "Asha", "0712000003", singleCard);

        get("/api/events", token)
                .andExpect(jsonPath("$.events[0].totalCards").value(3))
                .andExpect(jsonPath("$.events[0].totalSeats").value(5));
    }

    @Test
    void statusChangesFollowTheRules() throws Exception {
        TestCompany company = signUpNewCompany("Status Co");
        String token = company.ownerToken();
        String eventId = createEventAndGetId(token);

        // A draft cannot jump straight to finished
        put("/api/events/" + eventId + "/status", token, Map.of("status", "FINISHED"))
                .andExpect(status().isConflict());

        put("/api/events/" + eventId + "/status", token, Map.of("status", "ACTIVE")).andExpect(status().isOk());
        put("/api/events/" + eventId + "/status", token, Map.of("status", "FINISHED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowedNextStatuses").isEmpty());

        // A finished event is read-only
        put("/api/events/" + eventId, token, eventForm()).andExpect(status().isConflict());
        post("/api/events/" + eventId + "/card-types", token, Map.of("name", "VIP", "seats", 2))
                .andExpect(status().isConflict());
    }

    @Test
    void onlyTheOwnerCanDeleteAndOnlyDrafts() throws Exception {
        TestCompany company = signUpNewCompany("Delete Co");
        String managerToken = addStaffMember(company, "MANAGER");
        String draftId = createEventAndGetId(company.ownerToken());
        String activeId = createEventAndGetId(company.ownerToken());
        put("/api/events/" + activeId + "/status", company.ownerToken(), Map.of("status", "ACTIVE"));

        delete("/api/events/" + draftId, managerToken).andExpect(status().isForbidden());
        delete("/api/events/" + activeId, company.ownerToken()).andExpect(status().isConflict());

        delete("/api/events/" + draftId, company.ownerToken()).andExpect(status().isNoContent());
        get("/api/events/" + draftId, company.ownerToken()).andExpect(status().isNotFound());
    }

    @Test
    void checkInStaffCanViewEventsButNotChangeThem() throws Exception {
        TestCompany company = signUpNewCompany("Door Co");
        String checkInToken = addStaffMember(company, "CHECK_IN_STAFF");
        String eventId = createEventAndGetId(company.ownerToken());

        get("/api/events/" + eventId, checkInToken).andExpect(status().isOk());
        get("/api/events/" + eventId + "/guests", checkInToken).andExpect(status().isOk());
        post("/api/events", checkInToken, eventForm()).andExpect(status().isForbidden());
        put("/api/events/" + eventId, checkInToken, eventForm()).andExpect(status().isForbidden());
    }

    @Test
    void aCompanyCannotSeeOrTouchAnotherCompanysEvents() throws Exception {
        TestCompany companyA = signUpNewCompany("Company A");
        TestCompany companyB = signUpNewCompany("Company B");
        String eventOfA = createEventAndGetId(companyA.ownerToken());
        String doubleOfA = cardTypeId(companyA.ownerToken(), eventOfA, "Double");

        get("/api/events", companyB.ownerToken()).andExpect(jsonPath("$.totalEvents").value(0));
        get("/api/events/" + eventOfA, companyB.ownerToken()).andExpect(status().isNotFound());
        put("/api/events/" + eventOfA, companyB.ownerToken(), eventForm()).andExpect(status().isNotFound());
        get("/api/events/" + eventOfA + "/guests", companyB.ownerToken()).andExpect(status().isNotFound());
        post("/api/events/" + eventOfA + "/guests", companyB.ownerToken(), Map.of(
                "nameOnCard", "Intruder", "phone", "0712999999", "cardTypeId", doubleOfA))
                .andExpect(status().isNotFound());
        uploadFile("/api/events/" + eventOfA + "/guests/import", companyB.ownerToken(), "guests.csv",
                "Name,Phone\nIntruder,0712999999\n".getBytes())
                .andExpect(status().isNotFound());
        delete("/api/events/" + eventOfA, companyB.ownerToken()).andExpect(status().isNotFound());
    }

    private void addGuest(String token, String eventId, String name, String phone, String cardTypeId) throws Exception {
        post("/api/events/" + eventId + "/guests", token,
                Map.of("nameOnCard", name, "phone", phone, "cardTypeId", cardTypeId))
                .andExpect(status().isCreated());
    }

    private static Map<String, Object> eventForm() {
        Map<String, Object> form = new HashMap<>();
        form.put("name", "Test Wedding");
        form.put("eventType", "WEDDING");
        form.put("startsAt", "2026-12-12T16:00:00");
        form.put("venueName", "Serena Hotel");
        return form;
    }
}
