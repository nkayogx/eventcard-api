package com.kayogx.eventcard.billing;

import com.jayway.jsonpath.JsonPath;
import com.kayogx.eventcard.IntegrationTest;
import com.kayogx.eventcard.card.CardPictures;
import com.kayogx.eventcard.common.ConflictException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.awt.Color;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BillingTest extends IntegrationTest {

    @Autowired
    private CreditAccount creditAccount;

    @Test
    void newCompaniesStartOnTheFreePlan() throws Exception {
        TestCompany company = signUpNewCompanyOnFreePlan("Free Co");

        get("/api/billing", company.ownerToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan.code").value("FREE"))
                .andExpect(jsonPath("$.planStatus").value("FREE"))
                .andExpect(jsonPath("$.creditBalance").value(0))
                .andExpect(jsonPath("$.usage.staff").value(1))
                .andExpect(jsonPath("$.plansForSale.length()").value(3))
                .andExpect(jsonPath("$.packsForSale.length()").value(3));
    }

    @Test
    void theFreePlanLimitsAreEnforced() throws Exception {
        TestCompany company = signUpNewCompanyOnFreePlan("Free Co");
        String token = company.ownerToken();
        String firstEvent = createEventAndGetId(token);
        String secondEvent = createEventAndGetId(token);

        // 1 active event
        put("/api/events/" + firstEvent + "/status", token, Map.of("status", "ACTIVE")).andExpect(status().isOk());
        put("/api/events/" + secondEvent + "/status", token, Map.of("status", "ACTIVE"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.field").value("plan"));

        // 100 guests per event: an import of 101 is refused as a whole, and the preview warns
        String guestList = guestListFile(101);
        uploadFile("/api/events/" + secondEvent + "/guests/import/check", token, "guests.csv", guestList.getBytes())
                .andExpect(jsonPath("$.remainingGuestsOnPlan").value(100));
        uploadFile("/api/events/" + secondEvent + "/guests/import", token, "guests.csv", guestList.getBytes())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.field").value("plan"));
        get("/api/events/" + secondEvent + "/guests", token).andExpect(jsonPath("$.totalGuests").value(0));

        // 1 person (the owner) - no staff can be invited
        post("/api/my-company/staff/invitations", token, Map.of("email", uniqueEmail("staff"), "role", "MANAGER"))
                .andExpect(status().isConflict());

        // No custom domain, no own artwork
        post("/api/my-company/custom-domain", token, Map.of("domain", "invites-" + company.slug() + ".example.com"))
                .andExpect(status().isConflict());
        uploadFile("/api/events/" + firstEvent + "/card-design/background", token, "art.png",
                CardPictures.plainPng(400, 500, Color.WHITE))
                .andExpect(status().isConflict());
    }

    @Test
    void buyingAPlanSwitchesItOnWhenThePlatformAdminConfirms() throws Exception {
        TestCompany company = signUpNewCompanyOnFreePlan("Buyer Co");
        String token = company.ownerToken();
        String proPlanId = planRepository.findByCode("PRO").orElseThrow().getId().toString();

        String payment = post("/api/billing/payments", token, Map.of("type", "PLAN", "planId", proPlanId, "months", 3))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("WAITING_FOR_PAYMENT"))
                .andExpect(jsonPath("$.amountTzs").value(240_000))
                .andExpect(jsonPath("$.description").value("Pro plan - 3 months"))
                .andExpect(jsonPath("$.instructions.steps.length()").value(6))
                .andReturn().getResponse().getContentAsString();
        String paymentId = JsonPath.read(payment, "$.id");

        put("/api/billing/payments/" + paymentId + "/submit", token,
                Map.of("payerPhone", "0712345678", "transactionReference", "qk12ab34cd"))
                .andExpect(jsonPath("$.transactionReference").value("QK12AB34CD"));

        String adminToken = logInAsPlatformAdmin();
        get("/api/platform/payments", adminToken)
                .andExpect(jsonPath("$[?(@.id == '" + paymentId + "')].companyName").value("Buyer Co"));
        put("/api/platform/payments/" + paymentId + "/confirm", adminToken, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));
        put("/api/platform/payments/" + paymentId + "/confirm", adminToken, null)
                .andExpect(status().isConflict());   // can't be confirmed twice

        get("/api/billing", token)
                .andExpect(jsonPath("$.plan.code").value("PRO"))
                .andExpect(jsonPath("$.planStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.paidUntil").value(LocalDate.now().plusMonths(3).toString()));

        // One more month of the same plan is added on top
        String secondPayment = post("/api/billing/payments", token, Map.of("type", "PLAN", "planId", proPlanId, "months", 1))
                .andReturn().getResponse().getContentAsString();
        put("/api/platform/payments/" + JsonPath.read(secondPayment, "$.id") + "/confirm", adminToken, null);
        get("/api/billing", token)
                .andExpect(jsonPath("$.paidUntil").value(LocalDate.now().plusMonths(3).plusMonths(1).toString()));
    }

    @Test
    void onlyTheOwnerCanBuyAndOnlyThePlatformAdminCanConfirm() throws Exception {
        TestCompany company = signUpNewCompany("Roles Co");
        String managerToken = addStaffMember(company, "MANAGER");
        String packId = firstPackId();

        get("/api/billing", managerToken).andExpect(status().isOk());
        post("/api/billing/payments", managerToken, Map.of("type", "CREDITS", "creditPackId", packId))
                .andExpect(status().isForbidden());

        String payment = post("/api/billing/payments", company.ownerToken(), Map.of("type", "CREDITS", "creditPackId", packId))
                .andReturn().getResponse().getContentAsString();
        put("/api/platform/payments/" + JsonPath.read(payment, "$.id") + "/confirm", company.ownerToken(), null)
                .andExpect(status().isForbidden());
    }

    @Test
    void creditsAreAddedSpentAndExplainedOnTheStatement() throws Exception {
        TestCompany company = signUpNewCompany("Credits Co");
        String token = company.ownerToken();
        String adminToken = logInAsPlatformAdmin();

        String payment = post("/api/billing/payments", token, Map.of("type", "CREDITS", "creditPackId", firstPackId()))
                .andExpect(jsonPath("$.description").value("100 message credits"))
                .andReturn().getResponse().getContentAsString();
        put("/api/platform/payments/" + JsonPath.read(payment, "$.id") + "/confirm", adminToken, null);

        // The platform admin can gift or correct credits, but never below zero
        post("/api/platform/companies/" + company.companyId() + "/credits", adminToken, Map.of("amount", 50, "note", "Launch gift"))
                .andExpect(jsonPath("$.balanceAfter").value(150));
        post("/api/platform/companies/" + company.companyId() + "/credits", adminToken, Map.of("amount", -500, "note", "Too much"))
                .andExpect(status().isConflict());

        // Sending messages (sub-project 4) spends credits - never more than the company has
        allCompanies.run(() -> creditAccount.spend(company.companyId(), 20, CreditMovement.Reason.MESSAGE_SENT, "10 WhatsApp cards", null));
        assertThatThrownBy(() -> allCompanies.run(() ->
                creditAccount.spend(company.companyId(), 131, CreditMovement.Reason.MESSAGE_SENT, "Too many", null)))
                .isInstanceOf(ConflictException.class);

        get("/api/billing", token).andExpect(jsonPath("$.creditBalance").value(130));
        get("/api/billing/credit-movements", token)
                .andExpect(jsonPath("$.movements.length()").value(3))
                .andExpect(jsonPath("$.movements[0].reason").value("MESSAGE_SENT"))
                .andExpect(jsonPath("$.movements[0].amount").value(-20))
                .andExpect(jsonPath("$.movements[0].balanceAfter").value(130))
                .andExpect(jsonPath("$.movements[2].reason").value("PURCHASE"))
                .andExpect(jsonPath("$.movements[2].balanceAfter").value(100));
    }

    @Test
    void aRejectedPaymentTellsTheCompanyWhy() throws Exception {
        TestCompany company = signUpNewCompany("Reject Co");
        String payment = post("/api/billing/payments", company.ownerToken(), Map.of("type", "CREDITS", "creditPackId", firstPackId()))
                .andReturn().getResponse().getContentAsString();
        String paymentId = JsonPath.read(payment, "$.id");

        put("/api/platform/payments/" + paymentId + "/reject", logInAsPlatformAdmin(), Map.of("note", "No money received"))
                .andExpect(jsonPath("$.status").value("REJECTED"));
        get("/api/billing/payments", company.ownerToken())
                .andExpect(jsonPath("$[0].adminNote").value("No money received"))
                .andExpect(jsonPath("$[0].instructions").doesNotExist());
        get("/api/billing", company.ownerToken()).andExpect(jsonPath("$.creditBalance").value(0));
    }

    @Test
    void afterTheGracePeriodTheFreeLimitsApplyButGuestsAreNotAffected() throws Exception {
        TestCompany company = signUpNewCompany("Expiring Co");
        String token = company.ownerToken();
        String liveEvent = createEventAndGetId(token);
        String guest = post("/api/events/" + liveEvent + "/guests", token, Map.of(
                "nameOnCard", "Asha", "phone", "0712000001", "cardTypeId", cardTypeId(token, liveEvent, "Single")))
                .andReturn().getResponse().getContentAsString();
        put("/api/events/" + liveEvent + "/status", token, Map.of("status", "ACTIVE"));

        // Paid until 3 days ago: still within the 7 days' grace
        putOnPlan(company, "PRO", LocalDate.now().minusDays(3));
        get("/api/billing", token)
                .andExpect(jsonPath("$.planStatus").value("IN_GRACE"))
                .andExpect(jsonPath("$.plan.code").value("PRO"));

        // Paid until 10 days ago: back on Free limits
        putOnPlan(company, "PRO", LocalDate.now().minusDays(10));
        get("/api/billing", token)
                .andExpect(jsonPath("$.planStatus").value("EXPIRED"))
                .andExpect(jsonPath("$.plan.code").value("FREE"))
                .andExpect(jsonPath("$.chosenPlanName").value("Pro"));
        String anotherEvent = createEventAndGetId(token);   // drafts are still fine
        put("/api/events/" + anotherEvent + "/status", token, Map.of("status", "ACTIVE"))
                .andExpect(status().isConflict());

        // ...but the guest's invitation of the event that is already live still works
        get("/api/public/invitations/" + JsonPath.read(guest, "$.invitationCode"), null).andExpect(status().isOk());
    }

    @Test
    void companiesCannotSeeEachOthersPayments() throws Exception {
        TestCompany companyA = signUpNewCompany("Company A");
        TestCompany companyB = signUpNewCompany("Company B");
        String payment = post("/api/billing/payments", companyA.ownerToken(), Map.of("type", "CREDITS", "creditPackId", firstPackId()))
                .andReturn().getResponse().getContentAsString();

        get("/api/billing/payments", companyB.ownerToken()).andExpect(jsonPath("$.length()").value(0));
        put("/api/billing/payments/" + JsonPath.read(payment, "$.id") + "/submit", companyB.ownerToken(),
                Map.of("payerPhone", "0712345678", "transactionReference", "STEAL"))
                .andExpect(status().isNotFound());
    }

    @Test
    void thePlatformAdminSetsPlansAndPrices() throws Exception {
        String adminToken = logInAsPlatformAdmin();
        TestCompany company = signUpNewCompany("Curious Co");

        String plans = get("/api/platform/plans", adminToken).andReturn().getResponse().getContentAsString();
        List<String> starterIds = JsonPath.read(plans, "$[?(@.code == 'STARTER')].id");
        String starterId = starterIds.get(0);

        Map<String, Object> cheaperStarter = new HashMap<>();
        cheaperStarter.put("code", "STARTER");
        cheaperStarter.put("name", "Starter");
        cheaperStarter.put("monthlyPriceTzs", 25_000);
        cheaperStarter.put("maxActiveEvents", 5);
        cheaperStarter.put("maxGuestsPerEvent", 500);
        cheaperStarter.put("maxStaff", 3);
        cheaperStarter.put("allowsCustomDomain", false);
        cheaperStarter.put("allowsOwnArtwork", true);
        cheaperStarter.put("available", true);
        cheaperStarter.put("sortOrder", 2);
        put("/api/platform/plans/" + starterId, adminToken, cheaperStarter)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monthlyPriceTzs").value(25_000));
        put("/api/platform/plans/" + starterId, company.ownerToken(), cheaperStarter).andExpect(status().isForbidden());

        put("/api/platform/message-prices/WHATSAPP", adminToken, Map.of("credits", 3))
                .andExpect(jsonPath("$.credits").value(3));
        put("/api/platform/message-prices/WHATSAPP", adminToken, Map.of("credits", 2));   // put the default back
        assertThat(plans).contains("FREE", "PRO");
    }

    // ---------- helpers ----------

    private String firstPackId() throws Exception {
        TestCompany anyone = signUpNewCompanyOnFreePlan("Pack Finder");
        String overview = get("/api/billing", anyone.ownerToken()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(overview, "$.packsForSale[0].id");
    }

    private static String guestListFile(int guests) {
        StringBuilder file = new StringBuilder("Name,Phone\n");
        for (int number = 1; number <= guests; number++) {
            file.append("Guest ").append(number).append(",07").append(String.format("%08d", number)).append('\n');
        }
        return file.toString();
    }
}
