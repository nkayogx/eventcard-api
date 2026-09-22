package com.kayogx.eventcard.messaging;

import com.jayway.jsonpath.JsonPath;
import com.kayogx.eventcard.IntegrationTest;
import com.kayogx.eventcard.billing.CreditAccount;
import com.kayogx.eventcard.billing.CreditMovement;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SendingTest extends IntegrationTest {

    @Autowired
    private MessageWorker worker;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private CreditAccount creditAccount;

    /** A company allowed to send, with credits, and an ACTIVE event. */
    private record Setup(TestCompany company, String eventId) {

        String token() {
            return company.ownerToken();
        }
    }

    @Test
    void thePreviewShowsHowManyGuestsAndCredits() throws Exception {
        Setup setup = readyToSend(100);
        addGuest(setup, "Mr & Mrs Juma", "0712000001");
        addGuest(setup, "Asha Salim", "0712000002");

        preview(setup, "ALL", "WHATSAPP")
                .andExpect(jsonPath("$.guestCount").value(2))
                .andExpect(jsonPath("$.creditsNeeded").value(4))          // WhatsApp = 2 credits each
                .andExpect(jsonPath("$.creditBalance").value(100))
                .andExpect(jsonPath("$.enoughCredits").value(true))
                .andExpect(jsonPath("$.sampleSms", containsString("Asha Salim")))   // guests are sorted by name
                .andExpect(jsonPath("$.cannotSendReason").doesNotExist());

        // A long SMS text needs 2 parts per guest = 2 credits each
        put("/api/events/" + setup.eventId() + "/message-settings", setup.token(), Map.of("language", "EN",
                "smsText", "Hello {name}! " + "You are warmly invited to celebrate with us. ".repeat(4) + "Card: {link}"));
        preview(setup, "ALL", "SMS")
                .andExpect(jsonPath("$.creditsNeeded").value(4))
                .andExpect(jsonPath("$.sampleSmsParts").value(2));
    }

    @Test
    void sendingIsRefusedAsAWholeWhenCreditsAreShort() throws Exception {
        Setup setup = readyToSend(3);
        addGuest(setup, "Guest One", "0712000001");
        addGuest(setup, "Guest Two", "0712000002");

        send(setup, "ALL", "WHATSAPP")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.field").value("plan"));
        get("/api/events/" + setup.eventId() + "/sending", setup.token())
                .andExpect(jsonPath("$.totals.QUEUED").value(0));
        get("/api/billing", setup.token()).andExpect(jsonPath("$.creditBalance").value(3));
    }

    @Test
    void queuedCardsAreSentByTheWorkerAndDelivered() throws Exception {
        Setup setup = readyToSend(100);
        addGuest(setup, "Guest One", "0712000001");
        addGuest(setup, "Guest Two", "0712000002");

        send(setup, "ALL", "WHATSAPP")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.messageCount").value(2))
                .andExpect(jsonPath("$.creditsCharged").value(4));
        get("/api/events/" + setup.eventId() + "/sending", setup.token()).andExpect(jsonPath("$.totals.QUEUED").value(2));

        worker.sendDueMessages();

        get("/api/events/" + setup.eventId() + "/sending", setup.token())
                .andExpect(jsonPath("$.totals.DELIVERED").value(2))
                .andExpect(jsonPath("$.recentBatches[0].description").value("All guests"));
        get("/api/events/" + setup.eventId() + "/guests", setup.token())
                .andExpect(jsonPath("$.guests[0].cardStatus").value("DELIVERED"));
        get("/api/billing", setup.token()).andExpect(jsonPath("$.creditBalance").value(96));

        // "Not sent yet" now finds nobody
        preview(setup, "NOT_SENT", "WHATSAPP").andExpect(jsonPath("$.guestCount").value(0));
    }

    @Test
    void cardsCanBeSentToTickedGuestsOnly() throws Exception {
        Setup setup = readyToSend(100);
        String asha = addGuest(setup, "Asha", "0712000001");
        addGuest(setup, "Baraka", "0712000002");
        String juma = addGuest(setup, "Juma", "0712000003");
        Map<String, Object> ticked = Map.of("who", "SELECTED", "guestIds", List.of(asha, juma), "channel", "SMS");

        post("/api/events/" + setup.eventId() + "/sending/preview", setup.token(), ticked)
                .andExpect(jsonPath("$.guestCount").value(2));
        post("/api/events/" + setup.eventId() + "/sending", setup.token(), ticked)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.messageCount").value(2))
                .andExpect(jsonPath("$.description").value("Chosen guests"));

        // Baraka was not ticked, so he is the only one still "not sent"
        preview(setup, "NOT_SENT", "SMS").andExpect(jsonPath("$.guestCount").value(1));
    }

    @Test
    void aMessageThatFailsForGoodIsRefunded() throws Exception {
        Setup setup = readyToSend(100);
        addGuest(setup, "Not On WhatsApp", "0712340000");

        send(setup, "ALL", "WHATSAPP");
        worker.sendDueMessages();

        get("/api/events/" + setup.eventId() + "/sending", setup.token())
                .andExpect(jsonPath("$.totals.FAILED").value(1))
                .andExpect(jsonPath("$.recentFailures[0].failureReason").value("This number is not on WhatsApp"))
                .andExpect(jsonPath("$.recentFailures[0].creditsRefunded").value(true));
        get("/api/billing", setup.token()).andExpect(jsonPath("$.creditBalance").value(100));
        get("/api/billing/credit-movements", setup.token())
                .andExpect(jsonPath("$.movements[0].reason").value("MESSAGE_REFUND"));

        // "Failed only" picks the guest up again
        preview(setup, "FAILED", "SMS").andExpect(jsonPath("$.guestCount").value(1));
    }

    @Test
    void whatsAppFailuresCanFallBackToSms() throws Exception {
        Setup setup = readyToSend(100);
        addGuest(setup, "Not On WhatsApp", "0712340000");
        String smsPreview = preview(setup, "ALL", "SMS").andReturn().getResponse().getContentAsString();
        int smsCredits = JsonPath.read(smsPreview, "$.creditsNeeded");   // 1 credit per SMS part

        send(setup, "ALL", "WHATSAPP_THEN_SMS");        // 2 credits for WhatsApp
        worker.sendDueMessages();                      // WhatsApp fails: refunded, SMS queued and charged
        worker.sendDueMessages();                      // the SMS is sent

        List<Message> messages = allCompanies.run(() -> messageRepository.findByEventIdOrderByQueuedAtAsc(UUID.fromString(setup.eventId())));
        assertThat(messages).extracting(Message::getChannel).extracting(Enum::name).containsExactly("WHATSAPP", "SMS");
        assertThat(messages).extracting(Message::getStatus).containsExactly(MessageStatus.FAILED, MessageStatus.DELIVERED);
        get("/api/billing", setup.token()).andExpect(jsonPath("$.creditBalance").value(100 - smsCredits));
    }

    @Test
    void temporaryProblemsAreRetriedThenGivenUp() throws Exception {
        Setup setup = readyToSend(100);
        addGuest(setup, "Busy Service", "0712349999");
        send(setup, "ALL", "SMS");

        for (int attempt = 1; attempt <= 3; attempt++) {
            worker.sendDueMessages();
            Message waiting = onlyMessage(setup);
            assertThat(waiting.getStatus()).isEqualTo(MessageStatus.QUEUED);
            assertThat(waiting.getAttempts()).isEqualTo(attempt);
            assertThat(waiting.getNextAttemptAt()).isAfter(Instant.now());
            makeDueNow(waiting);
        }
        worker.sendDueMessages();   // 4th try: give up

        assertThat(onlyMessage(setup).getStatus()).isEqualTo(MessageStatus.FAILED);
        get("/api/billing", setup.token()).andExpect(jsonPath("$.creditBalance").value(100));
    }

    @Test
    void sendingNeedsAnActiveEventAnUnlockedCompanyAndTheRightRole() throws Exception {
        TestCompany company = signUpNewCompany("Locked Co");
        String eventId = createEventAndGetId(company.ownerToken());
        post("/api/events/" + eventId + "/guests", company.ownerToken(), Map.of(
                "nameOnCard", "Guest", "phone", "0712000001", "cardTypeId", cardTypeId(company.ownerToken(), eventId, "Single")));

        // Draft event
        post("/api/events/" + eventId + "/sending", company.ownerToken(), Map.of("who", "ALL", "channel", "SMS"))
                .andExpect(status().isConflict());

        // Active event, but the platform admin has not unlocked sending yet
        put("/api/events/" + eventId + "/status", company.ownerToken(), Map.of("status", "ACTIVE"));
        post("/api/events/" + eventId + "/sending", company.ownerToken(), Map.of("who", "ALL", "channel", "SMS"))
                .andExpect(status().isForbidden());
        get("/api/events/" + eventId + "/sending", company.ownerToken())
                .andExpect(jsonPath("$.cannotSendReason").value("Sending is locked until EventCard verifies your company"));

        // Check-in staff can't send at all
        String checkInToken = addStaffMember(company, "CHECK_IN_STAFF");
        get("/api/events/" + eventId + "/sending", checkInToken).andExpect(status().isForbidden());
    }

    @Test
    void oneGuestCanBeSentOrResentButNotTwiceAtOnce() throws Exception {
        Setup setup = readyToSend(100);
        String guestId = addGuest(setup, "Asha", "0712000001");
        String address = "/api/events/" + setup.eventId() + "/guests/" + guestId + "/send";

        post(address, setup.token(), Map.of("channel", "SMS")).andExpect(status().isCreated());
        post(address, setup.token(), Map.of("channel", "SMS")).andExpect(status().isConflict());   // still queued
        worker.sendDueMessages();
        post(address, setup.token(), Map.of("channel", "WHATSAPP")).andExpect(status().isCreated());  // resend

        get("/api/events/" + setup.eventId() + "/guests/" + guestId + "/messages", setup.token())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].channel").value("WHATSAPP"));
    }

    @Test
    void theSmsWordingUsesPlaceholdersAndTheChosenLanguage() throws Exception {
        Setup setup = readyToSend(100);
        addGuest(setup, "Mr & Mrs Juma", "0712000001");

        preview(setup, "ALL", "SMS").andExpect(jsonPath("$.sampleSms", containsString("Habari Mr & Mrs Juma")));

        put("/api/events/" + setup.eventId() + "/message-settings", setup.token(),
                Map.of("language", "EN", "smsText", "Dear {nmae}, see you at {event}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("{nmae}")));

        put("/api/events/" + setup.eventId() + "/message-settings", setup.token(),
                Map.of("language", "EN", "smsText", "Dear {name}, see you at {event}! {link}"))
                .andExpect(status().isOk());
        preview(setup, "ALL", "SMS")
                .andExpect(jsonPath("$.sampleSms", containsString("Dear Mr & Mrs Juma, see you at Asha & Baraka's Wedding! http")))
                .andExpect(jsonPath("$.sampleWhatsApp", containsString("Hello Mr & Mrs Juma")));
    }

    @Test
    void deliveryReportsUpdateTheStatusOnlyWhenGenuine() throws Exception {
        Setup setup = readyToSend(100);
        addGuest(setup, "WhatsApp Guest", "0712000001");
        addGuest(setup, "SMS Guest", "0712000002");
        send(setup, "ALL", "SMS");
        List<Message> messages = allCompanies.run(() -> messageRepository.findByEventIdOrderByQueuedAtAsc(UUID.fromString(setup.eventId())));
        pretendSentWithProviderId(messages.get(0), "wamid.TEST-" + setup.eventId());
        pretendSentWithProviderId(messages.get(1), "beem-" + setup.eventId());

        // Meta checks our webhook once when it is set up
        api.perform(MockMvcRequestBuilders.get("/api/public/webhooks/whatsapp")
                        .param("hub.mode", "subscribe").param("hub.verify_token", "test-verify-token").param("hub.challenge", "12345"))
                .andExpect(content().string("12345"));

        String report = """
                {"entry":[{"changes":[{"value":{"statuses":[{"id":"wamid.TEST-%s","status":"read"}]}}]}]}
                """.formatted(setup.eventId());
        whatsAppReport(report, "sha256=wrong").andExpect(status().isForbidden());
        whatsAppReport(report, signatureOf(report)).andExpect(status().isOk());
        assertThat(statusOf(messages.get(0))).isEqualTo(MessageStatus.READ);

        String beemReport = "{\"request_id\":\"beem-" + setup.eventId() + "\",\"status\":\"DELIVERED\"}";
        beemReport(beemReport, "wrong-token").andExpect(status().isForbidden());
        beemReport(beemReport, "test-beem-token").andExpect(status().isOk());
        assertThat(statusOf(messages.get(1))).isEqualTo(MessageStatus.DELIVERED);
    }

    @Test
    void aCompanyCannotSendOrSeeAnotherCompanysMessages() throws Exception {
        Setup setup = readyToSend(100);
        String guestId = addGuest(setup, "Asha", "0712000001");
        TestCompany otherCompany = signUpNewCompany("Other Co");

        get("/api/events/" + setup.eventId() + "/sending", otherCompany.ownerToken()).andExpect(status().isNotFound());
        post("/api/events/" + setup.eventId() + "/guests/" + guestId + "/send", otherCompany.ownerToken(),
                Map.of("channel", "SMS")).andExpect(status().isNotFound());
    }

    // ---------- helpers ----------

    private Setup readyToSend(long credits) throws Exception {
        TestCompany company = signUpNewCompany("Sender Co");
        put("/api/platform/companies/" + company.companyId() + "/allow-sending", logInAsPlatformAdmin(), null)
                .andExpect(status().isOk());
        allCompanies.run(() -> creditAccount.add(company.companyId(), credits, CreditMovement.Reason.ADMIN_ADJUSTMENT,
                "Test credits", null, null));
        String eventId = createEventAndGetId(company.ownerToken());
        put("/api/events/" + eventId + "/status", company.ownerToken(), Map.of("status", "ACTIVE")).andExpect(status().isOk());
        return new Setup(company, eventId);
    }

    private String addGuest(Setup setup, String name, String phone) throws Exception {
        String guest = post("/api/events/" + setup.eventId() + "/guests", setup.token(), Map.of(
                "nameOnCard", name, "phone", phone, "cardTypeId", cardTypeId(setup.token(), setup.eventId(), "Single")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(guest, "$.id");
    }

    private ResultActions preview(Setup setup, String who, String channel) throws Exception {
        return post("/api/events/" + setup.eventId() + "/sending/preview", setup.token(), Map.of("who", who, "channel", channel));
    }

    private ResultActions send(Setup setup, String who, String channel) throws Exception {
        return post("/api/events/" + setup.eventId() + "/sending", setup.token(), Map.of("who", who, "channel", channel));
    }

    private Message onlyMessage(Setup setup) {
        return allCompanies.run(() -> messageRepository.findByEventIdOrderByQueuedAtAsc(UUID.fromString(setup.eventId())).get(0));
    }

    private void makeDueNow(Message message) {
        allCompanies.run(() -> {
            messageRepository.findById(message.getId()).orElseThrow().setNextAttemptAt(Instant.now().minusSeconds(1));
            return null;
        });
    }

    private void pretendSentWithProviderId(Message message, String providerId) {
        allCompanies.run(() -> {
            Message saved = messageRepository.findById(message.getId()).orElseThrow();
            saved.setStatus(MessageStatus.SENT);
            saved.setProviderMessageId(providerId);
            return null;
        });
    }

    private MessageStatus statusOf(Message message) {
        return allCompanies.run(() -> messageRepository.findById(message.getId()).orElseThrow().getStatus());
    }

    private ResultActions whatsAppReport(String body, String signature) throws Exception {
        return api.perform(MockMvcRequestBuilders.post("/api/public/webhooks/whatsapp")
                .contentType(MediaType.APPLICATION_JSON).content(body).header("X-Hub-Signature-256", signature));
    }

    private ResultActions beemReport(String body, String token) throws Exception {
        return api.perform(MockMvcRequestBuilders.post("/api/public/webhooks/beem").param("token", token)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    /** Signs a report the way Meta does, with the test app secret. */
    private static String signatureOf(String body) throws Exception {
        Mac hmac = Mac.getInstance("HmacSHA256");
        hmac.init(new SecretKeySpec("test-whatsapp-app-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(hmac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }
}
