package com.kayogx.eventcard.guest;

import com.jayway.jsonpath.JsonPath;
import com.kayogx.eventcard.IntegrationTest;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GuestsTest extends IntegrationTest {

    @Test
    void addingAGuestCleansUpThePhoneNumber() throws Exception {
        TestCompany company = signUpNewCompany("Guests Co");
        String token = company.ownerToken();
        String eventId = createEventAndGetId(token);

        post("/api/events/" + eventId + "/guests", token, guest("Mr & Mrs Juma", "0712 345 678",
                cardTypeId(token, eventId, "Double"), "Bride's side"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.phone").value("+255712345678"))
                .andExpect(jsonPath("$.cardTypeName").value("Double"))
                .andExpect(jsonPath("$.seats").value(2));
    }

    @Test
    void aPhoneNumberCanBeOnAnEventsListOnlyOnce() throws Exception {
        TestCompany company = signUpNewCompany("Guests Co");
        String token = company.ownerToken();
        String firstEvent = createEventAndGetId(token);
        String secondEvent = createEventAndGetId(token);

        post("/api/events/" + firstEvent + "/guests", token,
                guest("Asha", "0712345678", cardTypeId(token, firstEvent, "Single"), null))
                .andExpect(status().isCreated());
        post("/api/events/" + firstEvent + "/guests", token,
                guest("Asha again", "+255712345678", cardTypeId(token, firstEvent, "Single"), null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.field").value("phone"));

        // The same person may be invited to a different event
        post("/api/events/" + secondEvent + "/guests", token,
                guest("Asha", "0712345678", cardTypeId(token, secondEvent, "Single"), null))
                .andExpect(status().isCreated());
    }

    @Test
    void aGuestMustUseACardTypeOfTheSameEvent() throws Exception {
        TestCompany company = signUpNewCompany("Guests Co");
        String token = company.ownerToken();
        String firstEvent = createEventAndGetId(token);
        String secondEvent = createEventAndGetId(token);

        post("/api/events/" + firstEvent + "/guests", token,
                guest("Asha", "0712345678", cardTypeId(token, secondEvent, "Single"), null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field").value("cardTypeId"));
    }

    @Test
    void theGuestListCanBeSearchedAndFiltered() throws Exception {
        TestCompany company = signUpNewCompany("Guests Co");
        String token = company.ownerToken();
        String eventId = createEventAndGetId(token);
        String single = cardTypeId(token, eventId, "Single");
        String doubleCard = cardTypeId(token, eventId, "Double");

        post("/api/events/" + eventId + "/guests", token, guest("Mr & Mrs Juma", "0712000001", doubleCard, "Bride's side"));
        post("/api/events/" + eventId + "/guests", token, guest("Asha Salim", "0712000002", single, "Groom's side"));
        post("/api/events/" + eventId + "/guests", token, guest("Baraka", "0712000003", single, "Bride's side"));

        get("/api/events/" + eventId + "/guests?search=juma", token)
                .andExpect(jsonPath("$.totalGuests").value(1))
                .andExpect(jsonPath("$.guests[0].nameOnCard").value("Mr & Mrs Juma"));
        get("/api/events/" + eventId + "/guests?group=Bride's side", token)
                .andExpect(jsonPath("$.totalGuests").value(2));
        get("/api/events/" + eventId + "/guests?cardTypeId=" + single, token)
                .andExpect(jsonPath("$.totalGuests").value(2))
                .andExpect(jsonPath("$.groupNames.length()").value(2));

        get("/api/events/" + eventId, token)
                .andExpect(jsonPath("$.groups[?(@.groupName == \"Bride's side\")].seats").value(3));
    }

    @Test
    void guestsCanBeEditedAndRemoved() throws Exception {
        TestCompany company = signUpNewCompany("Guests Co");
        String token = company.ownerToken();
        String eventId = createEventAndGetId(token);
        String single = cardTypeId(token, eventId, "Single");

        String created = post("/api/events/" + eventId + "/guests", token, guest("Asha", "0712000001", single, null))
                .andReturn().getResponse().getContentAsString();
        String guestId = JsonPath.read(created, "$.id");

        put("/api/events/" + eventId + "/guests/" + guestId, token,
                guest("Asha Salim", "0712000001", cardTypeId(token, eventId, "Double"), "Family"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nameOnCard").value("Asha Salim"))
                .andExpect(jsonPath("$.seats").value(2));

        delete("/api/events/" + eventId + "/guests/" + guestId, token).andExpect(status().isNoContent());
        get("/api/events/" + eventId + "/guests", token).andExpect(jsonPath("$.totalGuests").value(0));
    }

    private static Map<String, Object> guest(String name, String phone, String cardTypeId, String group) {
        Map<String, Object> form = new HashMap<>();
        form.put("nameOnCard", name);
        form.put("phone", phone);
        form.put("cardTypeId", cardTypeId);
        form.put("groupName", group);
        return form;
    }
}
