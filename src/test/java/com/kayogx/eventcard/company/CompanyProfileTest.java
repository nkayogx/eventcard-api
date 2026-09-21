package com.kayogx.eventcard.company;

import com.jayway.jsonpath.JsonPath;
import com.kayogx.eventcard.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.startsWith;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CompanyProfileTest extends IntegrationTest {

    private static final byte[] TINY_PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};

    @Test
    void theOwnerCanUpdateTheProfileIncludingBrandColours() throws Exception {
        TestCompany company = signUpNewCompany("Profile Co");

        Map<String, Object> profile = profileForm(company);
        profile.put("city", "Arusha");
        profile.put("primaryColor", "#8b1e3f");

        put("/api/my-company", company.ownerToken(), profile)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.city").value("Arusha"))
                .andExpect(jsonPath("$.primaryColor").value("#8B1E3F"));
    }

    @Test
    void managersCanViewButNotChangeTheProfile() throws Exception {
        TestCompany company = signUpNewCompany("Profile Co");
        String managerToken = addStaffMember(company, "MANAGER");

        get("/api/my-company", managerToken).andExpect(status().isOk());
        put("/api/my-company", managerToken, profileForm(company)).andExpect(status().isForbidden());
    }

    @Test
    void badColoursAndTakenShortNamesAreRefused() throws Exception {
        TestCompany company = signUpNewCompany("Profile Co");
        TestCompany otherCompany = signUpNewCompany("Other Co");

        Map<String, Object> badColour = profileForm(company);
        badColour.put("primaryColor", "red");
        put("/api/my-company", company.ownerToken(), badColour)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field").value("primaryColor"));

        Map<String, Object> takenSlug = profileForm(company);
        takenSlug.put("slug", otherCompany.slug());
        put("/api/my-company", company.ownerToken(), takenSlug)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.field").value("slug"));
    }

    @Test
    void theOwnerCanUploadALogo() throws Exception {
        TestCompany company = signUpNewCompany("Logo Co");
        var logo = new MockMultipartFile("file", "logo.png", "image/png", TINY_PNG);

        api.perform(multipart("/api/my-company/logo").file(logo)
                        .header("Authorization", "Bearer " + company.ownerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.logoUrl", startsWith("http://localhost:8181/uploads/logos/")));
    }

    @Test
    void aLogoMustReallyBeAnImage() throws Exception {
        TestCompany company = signUpNewCompany("Logo Co");
        var notAnImage = new MockMultipartFile("file", "logo.png", "image/png", "hello".getBytes());

        api.perform(multipart("/api/my-company/logo").file(notAnImage)
                        .header("Authorization", "Bearer " + company.ownerToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field").value("file"));
    }

    @Test
    void aCustomDomainIsVerifiedThroughADnsRecord() throws Exception {
        TestCompany company = signUpNewCompany("Domain Co");
        String domain = "invites-" + company.slug() + ".example.com";

        String answer = post("/api/my-company/custom-domain", company.ownerToken(), Map.of("domain", "https://" + domain + "/"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customDomain.domain").value(domain))
                .andExpect(jsonPath("$.customDomain.verified").value(false))
                .andExpect(jsonPath("$.customDomain.txtRecordName").value("_eventcard." + domain))
                .andReturn().getResponse().getContentAsString();
        String expectedValue = JsonPath.read(answer, "$.customDomain.txtRecordValue");

        // Before the vendor adds the DNS record, verification fails
        when(dnsTxtLookup.findTxtRecords("_eventcard." + domain)).thenReturn(List.of());
        postWithoutBody("/api/my-company/custom-domain/verify", company.ownerToken())
                .andExpect(status().isBadRequest());

        // After the vendor adds it, verification succeeds
        when(dnsTxtLookup.findTxtRecords("_eventcard." + domain)).thenReturn(List.of(expectedValue));
        postWithoutBody("/api/my-company/custom-domain/verify", company.ownerToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customDomain.verified").value(true));
    }

    @Test
    void twoCompaniesCannotUseTheSameDomain() throws Exception {
        TestCompany companyA = signUpNewCompany("Domain A");
        TestCompany companyB = signUpNewCompany("Domain B");
        String domain = "shared-" + companyA.slug() + ".example.com";

        post("/api/my-company/custom-domain", companyA.ownerToken(), Map.of("domain", domain))
                .andExpect(status().isOk());
        post("/api/my-company/custom-domain", companyB.ownerToken(), Map.of("domain", domain))
                .andExpect(status().isConflict());
    }

    private static Map<String, Object> profileForm(TestCompany company) {
        Map<String, Object> form = new HashMap<>();
        form.put("name", "Profile Co");
        form.put("slug", company.slug());
        form.put("contactPhone", "+255712345678");
        form.put("contactEmail", company.ownerEmail());
        form.put("countryCode", "TZ");
        form.put("timeZone", "Africa/Dar_es_Salaam");
        return form;
    }
}
