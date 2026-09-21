package com.kayogx.eventcard.card;

import com.jayway.jsonpath.JsonPath;
import com.kayogx.eventcard.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CardDesignTest extends IntegrationTest {

    @Value("${app.card-cache-folder}")
    private String cardCacheFolder;

    @Test
    void aNewEventStartsWithTheClassicTemplate() throws Exception {
        TestCompany company = signUpNewCompany("Design Co");
        String eventId = createEventAndGetId(company.ownerToken());

        get("/api/events/" + eventId + "/card-design", company.ownerToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("TEMPLATE"))
                .andExpect(jsonPath("$.templateName").value("CLASSIC"))
                .andExpect(jsonPath("$.cardTypes.length()").value(2));
    }

    @Test
    void everyTemplateCanBeDrawn() throws Exception {
        TestCompany company = signUpNewCompany("Design Co");
        String eventId = createEventAndGetId(company.ownerToken());

        for (String template : List.of("CLASSIC", "ELEGANT", "MODERN")) {
            byte[] card = get("/api/events/" + eventId + "/card-design/preview.png?template=" + template, company.ownerToken())
                    .andExpect(status().isOk())
                    .andExpect(content().contentType("image/png"))
                    .andReturn().getResponse().getContentAsByteArray();
            BufferedImage picture = CardPictures.read(card);
            assertThat(picture.getWidth()).isEqualTo(1080);
            assertThat(picture.getHeight()).isEqualTo(1350);
        }
    }

    @Test
    void aGuestsCardCarriesTheirPersonalLinkInTheQrCode() throws Exception {
        TestCompany company = signUpNewCompany("Design Co");
        String token = company.ownerToken();
        String eventId = createEventAndGetId(token);

        uploadFile("/api/events/" + eventId + "/card-design/background", token, "art.png",
                CardPictures.plainPng(800, 1000, new Color(0xF4E4C1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("UPLOADED"))
                .andExpect(jsonPath("$.width").value(800))
                .andExpect(jsonPath("$.fields.length()").value(3));

        String guest = addGuest(token, eventId, "Mr & Mrs Juma", "0712000001", "Double");
        byte[] card = get("/api/events/" + eventId + "/guests/" + JsonPath.read(guest, "$.id") + "/card.png", token)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(CardPictures.read(card).getWidth()).isEqualTo(800);
        assertThat(CardPictures.readQrCode(card)).isEqualTo(JsonPath.read(guest, "$.invitationLink"));
        assertThat((String) JsonPath.read(guest, "$.invitationLink")).startsWith("http://localhost:5173/i/");
    }

    @Test
    void theTemplateCardQrCodeCanBeReadToo() throws Exception {
        TestCompany company = signUpNewCompany("Design Co");
        String token = company.ownerToken();
        String eventId = createEventAndGetId(token);
        String guest = addGuest(token, eventId, "Asha Salim", "0712000001", "Single");

        byte[] card = get("/api/events/" + eventId + "/guests/" + JsonPath.read(guest, "$.id") + "/card.png", token)
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(CardPictures.readQrCode(card)).isEqualTo(JsonPath.read(guest, "$.invitationLink"));
    }

    @Test
    void boxesCanBeMovedButMustStayInsideThePicture() throws Exception {
        TestCompany company = signUpNewCompany("Design Co");
        String token = company.ownerToken();
        String eventId = createEventAndGetId(token);
        uploadFile("/api/events/" + eventId + "/card-design/background", token, "art.png",
                CardPictures.plainPng(800, 1000, Color.WHITE));

        Map<String, Object> nameBox = box("GUEST_NAME", 5, 20, 90, 12);
        put("/api/events/" + eventId + "/card-design", token, Map.of("kind", "UPLOADED", "fields", List.of(nameBox)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fields[?(@.field == 'GUEST_NAME')].y").value(20.0))
                .andExpect(jsonPath("$.version").value(3));  // 1 when created, 2 after upload, 3 after this save

        Map<String, Object> outside = box("GUEST_NAME", 50, 20, 60, 12);  // 50% + 60% goes past the right edge
        put("/api/events/" + eventId + "/card-design", token, Map.of("kind", "UPLOADED", "fields", List.of(outside)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aCardTypeCanHaveItsOwnArtworkOfTheSameSize() throws Exception {
        TestCompany company = signUpNewCompany("Design Co");
        String token = company.ownerToken();
        String eventId = createEventAndGetId(token);
        String vip = cardTypeId(token, eventId, "Double");

        // Only possible once the main artwork is uploaded
        uploadFile("/api/events/" + eventId + "/card-types/" + vip + "/background", token, "gold.png",
                CardPictures.plainPng(800, 1000, Color.YELLOW))
                .andExpect(status().isConflict());

        uploadFile("/api/events/" + eventId + "/card-design/background", token, "art.png",
                CardPictures.plainPng(800, 1000, Color.WHITE));
        uploadFile("/api/events/" + eventId + "/card-types/" + vip + "/background", token, "small.png",
                CardPictures.plainPng(400, 500, Color.YELLOW))
                .andExpect(status().isBadRequest());
        uploadFile("/api/events/" + eventId + "/card-types/" + vip + "/background", token, "gold.png",
                CardPictures.plainPng(800, 1000, Color.YELLOW))
                .andExpect(status().isOk());

        String doubleGuest = addGuest(token, eventId, "VIP Guest", "0712000001", "Double");
        String singleGuest = addGuest(token, eventId, "Plain Guest", "0712000002", "Single");
        assertThat(topLeftColour(token, eventId, doubleGuest)).isEqualTo(Color.YELLOW.getRGB());
        assertThat(topLeftColour(token, eventId, singleGuest)).isEqualTo(Color.WHITE.getRGB());
    }

    @Test
    void cardsAreDrawnOnceAndRedrawnWhenSomethingChanges() throws Exception {
        TestCompany company = signUpNewCompany("Design Co");
        String token = company.ownerToken();
        String eventId = createEventAndGetId(token);
        String guest = addGuest(token, eventId, "Asha", "0712000001", "Single");
        String cardAddress = "/api/events/" + eventId + "/guests/" + JsonPath.read(guest, "$.id") + "/card.png";

        byte[] first = get(cardAddress, token).andReturn().getResponse().getContentAsByteArray();
        long savedCards = countSavedCards();
        byte[] second = get(cardAddress, token).andReturn().getResponse().getContentAsByteArray();
        assertThat(second).isEqualTo(first);
        assertThat(countSavedCards()).isEqualTo(savedCards);  // reused, not drawn again

        Map<String, Object> renamed = new HashMap<>();
        renamed.put("nameOnCard", "Asha Salim");
        renamed.put("phone", "0712000001");
        renamed.put("cardTypeId", cardTypeId(token, eventId, "Single"));
        put("/api/events/" + eventId + "/guests/" + JsonPath.read(guest, "$.id"), token, renamed);

        byte[] afterRename = get(cardAddress, token).andReturn().getResponse().getContentAsByteArray();
        assertThat(afterRename).isNotEqualTo(first);
    }

    @Test
    void checkInStaffCanLookButNotChangeTheDesign() throws Exception {
        TestCompany company = signUpNewCompany("Design Co");
        String checkInToken = addStaffMember(company, "CHECK_IN_STAFF");
        String eventId = createEventAndGetId(company.ownerToken());

        get("/api/events/" + eventId + "/card-design", checkInToken).andExpect(status().isOk());
        put("/api/events/" + eventId + "/card-design", checkInToken, Map.of("kind", "TEMPLATE", "templateName", "MODERN"))
                .andExpect(status().isForbidden());
    }

    @Test
    void aCompanyCannotSeeAnotherCompanysDesignOrCards() throws Exception {
        TestCompany companyA = signUpNewCompany("Company A");
        TestCompany companyB = signUpNewCompany("Company B");
        String eventOfA = createEventAndGetId(companyA.ownerToken());
        String guestOfA = addGuest(companyA.ownerToken(), eventOfA, "Secret Guest", "0712000001", "Single");

        get("/api/events/" + eventOfA + "/card-design", companyB.ownerToken()).andExpect(status().isNotFound());
        get("/api/events/" + eventOfA + "/guests/" + JsonPath.read(guestOfA, "$.id") + "/card.png", companyB.ownerToken())
                .andExpect(status().isNotFound());
    }

    // ---------- helpers ----------

    private String addGuest(String token, String eventId, String name, String phone, String cardType) throws Exception {
        return post("/api/events/" + eventId + "/guests", token,
                Map.of("nameOnCard", name, "phone", phone, "cardTypeId", cardTypeId(token, eventId, cardType)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    private int topLeftColour(String token, String eventId, String guest) throws Exception {
        byte[] card = get("/api/events/" + eventId + "/guests/" + JsonPath.read(guest, "$.id") + "/card.png", token)
                .andReturn().getResponse().getContentAsByteArray();
        return CardPictures.read(card).getRGB(2, 2);
    }

    private long countSavedCards() throws Exception {
        Path folder = Path.of(cardCacheFolder);
        if (!Files.exists(folder)) {
            return 0;
        }
        try (Stream<Path> files = Files.list(folder)) {
            return files.count();
        }
    }

    private static Map<String, Object> box(String field, double x, double y, double width, double height) {
        Map<String, Object> box = new HashMap<>();
        box.put("field", field);
        box.put("x", x);
        box.put("y", y);
        box.put("width", width);
        box.put("height", height);
        box.put("font", "PLAYFAIR_BOLD");
        box.put("fontSize", 48);
        box.put("color", "#1F1A1C");
        box.put("align", "CENTER");
        box.put("visible", true);
        return box;
    }
}
