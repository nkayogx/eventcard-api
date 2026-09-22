package com.kayogx.eventcard.checkin;

import com.jayway.jsonpath.JsonPath;
import com.kayogx.eventcard.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Map;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CheckInTest extends IntegrationTest {

    /** An ACTIVE event with one Double guest (2 seats). */
    private record Setup(TestCompany company, String eventId, String guestId, String invitationLink, String code) {
    }

    @Test
    void scanningTheCardLinkOrTheBareCodeFindsTheGuest() throws Exception {
        Setup setup = activeEventWithDoubleGuest();
        String token = setup.company().ownerToken();

        lookUp(setup, token, setup.invitationLink())
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.guest.nameOnCard").value("Mr & Mrs Juma"))
                .andExpect(jsonPath("$.guest.seats").value(2))
                .andExpect(jsonPath("$.guest.seatsLeft").value(2));
        lookUp(setup, token, "  " + setup.code() + " ")      // a barcode scanner may send just the code
                .andExpect(jsonPath("$.guest.id").value(setup.guestId()));
    }

    @Test
    void aDoubleCardCanBeUsedByOnePersonNowAndOneLaterButNotMore() throws Exception {
        Setup setup = activeEventWithDoubleGuest();
        String checkInToken = addStaffMember(setup.company(), "CHECK_IN_STAFF");

        checkIn(setup, checkInToken, 1)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.peopleArrived").value(1))
                .andExpect(jsonPath("$.seatsLeft").value(1));
        lookUp(setup, checkInToken, setup.invitationLink()).andExpect(jsonPath("$.status").value("PARTLY_ARRIVED"));

        checkIn(setup, checkInToken, 2)                   // only 1 seat left
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Only 1 seat is left on this card"));
        checkIn(setup, checkInToken, 1).andExpect(jsonPath("$.seatsLeft").value(0));

        lookUp(setup, checkInToken, setup.invitationLink()).andExpect(jsonPath("$.status").value("ALL_ARRIVED"));
        checkIn(setup, checkInToken, 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", startsWith("All 2 already arrived at ")));
    }

    @Test
    void theSummaryCountsPeopleAndCards() throws Exception {
        Setup setup = activeEventWithDoubleGuest();
        String token = setup.company().ownerToken();
        post("/api/events/" + setup.eventId() + "/guests", token, Map.of(
                "nameOnCard", "Asha", "phone", "0712000002", "cardTypeId", cardTypeId(token, setup.eventId(), "Single")));

        checkIn(setup, token, 2);

        get("/api/events/" + setup.eventId() + "/check-in/summary", token)
                .andExpect(jsonPath("$.peopleArrived").value(2))
                .andExpect(jsonPath("$.totalSeats").value(3))
                .andExpect(jsonPath("$.cardsArrived").value(1))
                .andExpect(jsonPath("$.totalCards").value(2))
                .andExpect(jsonPath("$.recentCheckIns[0].guestName").value("Mr & Mrs Juma"))
                .andExpect(jsonPath("$.recentCheckIns[0].people").value(2));
        get("/api/events/" + setup.eventId() + "/guests?search=Juma", token)
                .andExpect(jsonPath("$.guests[0].peopleArrived").value(2));
    }

    @Test
    void guestsWithoutTheirCardCanBeFoundByNameOrPhone() throws Exception {
        Setup setup = activeEventWithDoubleGuest();
        String checkInToken = addStaffMember(setup.company(), "CHECK_IN_STAFF");

        get("/api/events/" + setup.eventId() + "/check-in/search?q=juma", checkInToken)
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(setup.guestId()));
        get("/api/events/" + setup.eventId() + "/check-in/search?q=0001", checkInToken)
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void onlyManagersAndOwnersCanUndoAndUndoFreesTheSeats() throws Exception {
        Setup setup = activeEventWithDoubleGuest();
        String checkInToken = addStaffMember(setup.company(), "CHECK_IN_STAFF");
        checkIn(setup, checkInToken, 2);
        String summary = get("/api/events/" + setup.eventId() + "/check-in/summary", checkInToken)
                .andReturn().getResponse().getContentAsString();
        String checkInId = JsonPath.read(summary, "$.recentCheckIns[0].id");
        String undoAddress = "/api/events/" + setup.eventId() + "/check-ins/" + checkInId + "/undo";

        postWithoutBody(undoAddress, checkInToken).andExpect(status().isForbidden());
        postWithoutBody(undoAddress, setup.company().ownerToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.peopleArrived").value(0))
                .andExpect(jsonPath("$.seatsLeft").value(2));
        postWithoutBody(undoAddress, setup.company().ownerToken()).andExpect(status().isConflict());
    }

    @Test
    void checkInIsOnlyForActiveEvents() throws Exception {
        Setup setup = activeEventWithDoubleGuest();
        put("/api/events/" + setup.eventId() + "/status", setup.company().ownerToken(), Map.of("status", "FINISHED"));

        lookUp(setup, setup.company().ownerToken(), setup.code()).andExpect(status().isConflict());
        checkIn(setup, setup.company().ownerToken(), 1).andExpect(status().isConflict());
    }

    @Test
    void aCardOfAnotherEventOrCompanyIsNotLetIn() throws Exception {
        Setup setup = activeEventWithDoubleGuest();
        Setup otherCompany = activeEventWithDoubleGuest();
        String token = setup.company().ownerToken();

        // A card from another company: "not for this event", and nothing about that guest is shown
        lookUp(setup, token, otherCompany.invitationLink())
                .andExpect(jsonPath("$.status").value("NOT_FOR_THIS_EVENT"))
                .andExpect(jsonPath("$.guest").doesNotExist());

        // A card from another event of the same company
        String secondEvent = createEventAndGetId(token);
        put("/api/events/" + secondEvent + "/status", token, Map.of("status", "ACTIVE"));
        post("/api/events/" + secondEvent + "/check-in/look-up", token, Map.of("scanned", setup.code()))
                .andExpect(jsonPath("$.status").value("NOT_FOR_THIS_EVENT"));

        // Another company cannot check in, search or see this event at all
        post("/api/events/" + setup.eventId() + "/check-in", otherCompany.company().ownerToken(),
                Map.of("guestId", setup.guestId(), "people", 1, "method", "QR_SCAN"))
                .andExpect(status().isNotFound());
        get("/api/events/" + setup.eventId() + "/check-in/summary", otherCompany.company().ownerToken())
                .andExpect(status().isNotFound());
    }

    // ---------- helpers ----------

    private Setup activeEventWithDoubleGuest() throws Exception {
        TestCompany company = signUpNewCompany("Door Co");
        String token = company.ownerToken();
        String eventId = createEventAndGetId(token);
        String guest = post("/api/events/" + eventId + "/guests", token, Map.of(
                "nameOnCard", "Mr & Mrs Juma", "phone", "0712000001", "cardTypeId", cardTypeId(token, eventId, "Double")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        put("/api/events/" + eventId + "/status", token, Map.of("status", "ACTIVE")).andExpect(status().isOk());
        return new Setup(company, eventId, JsonPath.read(guest, "$.id"),
                JsonPath.read(guest, "$.invitationLink"), JsonPath.read(guest, "$.invitationCode"));
    }

    private ResultActions lookUp(Setup setup, String token, String scanned) throws Exception {
        return post("/api/events/" + setup.eventId() + "/check-in/look-up", token, Map.of("scanned", scanned));
    }

    private ResultActions checkIn(Setup setup, String token, int people) throws Exception {
        return post("/api/events/" + setup.eventId() + "/check-in", token,
                Map.of("guestId", setup.guestId(), "people", people, "method", "QR_SCAN"));
    }
}
