package com.kayogx.eventcard.event;

import com.kayogx.eventcard.IntegrationTest;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CardTypesTest extends IntegrationTest {

    @Test
    void vendorsCanAddTheirOwnCardTypes() throws Exception {
        TestCompany company = signUpNewCompany("Cards Co");
        String eventId = createEventAndGetId(company.ownerToken());

        post("/api/events/" + eventId + "/card-types", company.ownerToken(), Map.of("name", "VIP", "seats", 2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cardTypes[2].name").value("VIP"))
                .andExpect(jsonPath("$.cardTypes[2].seats").value(2));
    }

    @Test
    void cardTypeNamesMustBeUniqueWithinAnEvent() throws Exception {
        TestCompany company = signUpNewCompany("Cards Co");
        String eventId = createEventAndGetId(company.ownerToken());

        post("/api/events/" + eventId + "/card-types", company.ownerToken(), Map.of("name", "double", "seats", 2))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.field").value("name"));
    }

    @Test
    void aCardTypeInUseCannotBeDeleted() throws Exception {
        TestCompany company = signUpNewCompany("Cards Co");
        String token = company.ownerToken();
        String eventId = createEventAndGetId(token);
        String doubleCard = cardTypeId(token, eventId, "Double");
        post("/api/events/" + eventId + "/guests", token,
                Map.of("nameOnCard", "Mr & Mrs Juma", "phone", "0712000001", "cardTypeId", doubleCard));

        delete("/api/events/" + eventId + "/card-types/" + doubleCard, token)
                .andExpect(status().isConflict());
    }

    @Test
    void theLastCardTypeCannotBeDeleted() throws Exception {
        TestCompany company = signUpNewCompany("Cards Co");
        String token = company.ownerToken();
        String eventId = createEventAndGetId(token);

        delete("/api/events/" + eventId + "/card-types/" + cardTypeId(token, eventId, "Double"), token)
                .andExpect(status().isOk());
        delete("/api/events/" + eventId + "/card-types/" + cardTypeId(token, eventId, "Single"), token)
                .andExpect(status().isConflict());
    }

    @Test
    void changingSeatsUpdatesTheTotals() throws Exception {
        TestCompany company = signUpNewCompany("Cards Co");
        String token = company.ownerToken();
        String eventId = createEventAndGetId(token);
        String doubleCard = cardTypeId(token, eventId, "Double");
        post("/api/events/" + eventId + "/guests", token,
                Map.of("nameOnCard", "Family Juma", "phone", "0712000001", "cardTypeId", doubleCard));

        put("/api/events/" + eventId + "/card-types/" + doubleCard, token, Map.of("name", "Family", "seats", 4))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCards").value(1))
                .andExpect(jsonPath("$.totalSeats").value(4));
    }
}
