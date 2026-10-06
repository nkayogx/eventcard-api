package com.kayogx.eventcard.invitation;

import com.jayway.jsonpath.JsonPath;
import com.kayogx.eventcard.IntegrationTest;
import com.kayogx.eventcard.config.InvitationCodeFiller;
import com.kayogx.eventcard.model.Guest;
import com.kayogx.eventcard.repository.GuestRepository;
import com.kayogx.eventcard.service.AllCompaniesTransaction;
import com.kayogx.eventcard.service.WrongCodeLimiter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PublicInvitationTest extends IntegrationTest {

    @Autowired
    private GuestRepository guestRepository;

    @Autowired
    private AllCompaniesTransaction allCompaniesTransaction;

    @Autowired
    private InvitationCodeFiller invitationCodeFiller;

    /** A company with an event and one "Double" guest. */
    private record Setup(TestCompany company, String eventId, String guestCode) {
    }

    @Test
    void invitationsOpenOnlyOnceTheEventIsActive() throws Exception {
        Setup setup = eventWithOneGuest();
        String page = "/api/public/invitations/" + setup.guestCode();

        publicGet(page).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("This invitation is not available"));

        activate(setup);
        publicGet(page)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.guestName").value("Mr & Mrs Juma"))
                .andExpect(jsonPath("$.seats").value(2))
                .andExpect(jsonPath("$.rsvpStatus").value("NO_REPLY"))
                .andExpect(jsonPath("$.rsvpOpen").value(true))
                .andExpect(jsonPath("$.event.venueName").value("Serena Hotel"))
                .andExpect(jsonPath("$.company.name").value("Invite Co"));
        publicGet(page + "/card.png")
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"));
    }

    @Test
    void guestsCanSayYesWithHowManyPeopleComing() throws Exception {
        Setup setup = eventWithOneGuest();
        activate(setup);
        String rsvp = "/api/public/invitations/" + setup.guestCode() + "/rsvp";

        post(rsvp, null, Map.of("attending", true, "people", 3))   // a Double card has only 2 seats
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field").value("people"));

        post(rsvp, null, Map.of("attending", true, "people", 2, "message", "Hongera!"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rsvpStatus").value("ATTENDING"))
                .andExpect(jsonPath("$.rsvpPeople").value(2));

        get("/api/events/" + setup.eventId(), setup.company().ownerToken())
                .andExpect(jsonPath("$.rsvp.attendingCards").value(1))
                .andExpect(jsonPath("$.rsvp.attendingPeople").value(2))
                .andExpect(jsonPath("$.rsvp.noReplyCards").value(0));
        get("/api/events/" + setup.eventId() + "/guests?rsvp=ATTENDING", setup.company().ownerToken())
                .andExpect(jsonPath("$.guests[0].rsvpMessage").value("Hongera!"));
    }

    @Test
    void guestsCanChangeTheirMindAndSayNo() throws Exception {
        Setup setup = eventWithOneGuest();
        activate(setup);
        String rsvp = "/api/public/invitations/" + setup.guestCode() + "/rsvp";

        post(rsvp, null, Map.of("attending", true)).andExpect(jsonPath("$.rsvpPeople").value(2));
        post(rsvp, null, Map.of("attending", false))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rsvpStatus").value("NOT_ATTENDING"))
                .andExpect(jsonPath("$.rsvpPeople").doesNotExist());
    }

    @Test
    void rsvpClosesAfterTheDeadline() throws Exception {
        Setup setup = eventWithOneGuest();
        activate(setup);
        Map<String, Object> form = new HashMap<>();
        form.put("name", "Past Deadline Wedding");
        form.put("eventType", "WEDDING");
        form.put("startsAt", "2026-12-12T16:00:00");
        form.put("venueName", "Serena Hotel");
        form.put("rsvpDeadline", LocalDate.now().minusDays(3).toString());
        put("/api/events/" + setup.eventId(), setup.company().ownerToken(), form).andExpect(status().isOk());

        publicGet("/api/public/invitations/" + setup.guestCode()).andExpect(jsonPath("$.rsvpOpen").value(false));
        post("/api/public/invitations/" + setup.guestCode() + "/rsvp", null, Map.of("attending", true))
                .andExpect(status().isConflict());
    }

    @Test
    void guessingCodesIsSlowedDown() throws Exception {
        for (int attempt = 0; attempt < WrongCodeLimiter.MAX_WRONG_CODES_PER_MINUTE; attempt++) {
            fromAddress("/api/public/invitations/WRONG" + attempt, "10.20.30.40").andExpect(status().isNotFound());
        }
        fromAddress("/api/public/invitations/WRONG-again", "10.20.30.40").andExpect(status().isTooManyRequests());
        // Other people are not affected
        fromAddress("/api/public/invitations/WRONG-other", "10.20.30.41").andExpect(status().isNotFound());
    }

    @Test
    void linksUseTheCompanysOwnVerifiedDomain() throws Exception {
        Setup setup = eventWithOneGuest();
        String token = setup.company().ownerToken();
        String domain = "invites-" + setup.company().slug() + ".example.com";

        String answer = post("/api/my-company/custom-domain", token, Map.of("domain", domain))
                .andExpect(jsonPath("$.customDomain.cnameTarget").value("invites.eventcard.app"))
                .andReturn().getResponse().getContentAsString();
        String dnsValue = JsonPath.read(answer, "$.customDomain.txtRecordValue");
        when(dnsTxtLookup.findTxtRecords("_eventcard." + domain)).thenReturn(List.of(dnsValue));
        postWithoutBody("/api/my-company/custom-domain/verify", token).andExpect(status().isOk());

        get("/api/events/" + setup.eventId() + "/guests", token)
                .andExpect(jsonPath("$.guests[0].invitationLink")
                        .value("https://" + domain + "/i/" + setup.guestCode()));

        // The web server may get an HTTPS certificate for this verified domain, but not for unknown ones
        publicGet("/api/public/domains/allowed?domain=" + domain).andExpect(status().isOk());
        publicGet("/api/public/domains/allowed?domain=unknown.example.com").andExpect(status().isNotFound());
    }

    @Test
    void everyGuestHasTheirOwnCodeIncludingOlderGuests() throws Exception {
        Setup setup = eventWithOneGuest();
        String guestList = get("/api/events/" + setup.eventId() + "/guests", setup.company().ownerToken())
                .andReturn().getResponse().getContentAsString();
        UUID guestId = UUID.fromString(JsonPath.read(guestList, "$.guests[0].id"));

        // Pretend this guest was added before invitation codes existed
        allCompaniesTransaction.run(() -> {
            guestRepository.findById(guestId).orElseThrow().setInvitationCode(null);
            return null;
        });
        invitationCodeFiller.run(null);

        Guest filledIn = allCompaniesTransaction.run(() -> guestRepository.findById(guestId).orElseThrow());
        assertThat(filledIn.getInvitationCode()).hasSize(12).isNotEqualTo(setup.guestCode());
    }

    // ---------- helpers ----------

    private Setup eventWithOneGuest() throws Exception {
        TestCompany company = signUpNewCompany("Invite Co");
        String token = company.ownerToken();
        String eventId = createEventAndGetId(token);
        String guest = post("/api/events/" + eventId + "/guests", token, Map.of(
                "nameOnCard", "Mr & Mrs Juma", "phone", "0712000001",
                "cardTypeId", cardTypeId(token, eventId, "Double")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return new Setup(company, eventId, JsonPath.read(guest, "$.invitationCode"));
    }

    private void activate(Setup setup) throws Exception {
        put("/api/events/" + setup.eventId() + "/status", setup.company().ownerToken(), Map.of("status", "ACTIVE"))
                .andExpect(status().isOk());
    }

    /** A guest opening a public address from a unique internet address (so tests don't slow each other down). */
    private ResultActions publicGet(String address) throws Exception {
        return fromAddress(address, "192.168.0." + (int) (Math.random() * 250));
    }

    private ResultActions fromAddress(String address, String internetAddress)
            throws Exception {
        return api.perform(MockMvcRequestBuilders.get(address).with(request -> {
            request.setRemoteAddr(internetAddress);
            return request;
        }));
    }
}
