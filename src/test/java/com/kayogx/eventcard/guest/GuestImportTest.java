package com.kayogx.eventcard.guest;

import com.kayogx.eventcard.IntegrationTest;
import com.kayogx.eventcard.service.GuestFileReader;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GuestImportTest extends IntegrationTest {

    /**
     * A small guest list with one of everything:
     * row 2 good, row 3 good (no card type = first type), row 4 bad phone, row 5 unknown card type,
     * row 6 duplicate of row 2 (same phone written differently), row 7 missing name, row 8 empty (ignored).
     */
    private static final String MIXED_CSV = """
            Name,Phone,Card type,Group,Notes
            Mr & Mrs Juma,0712 000 001,Double,Bride's side,
            Asha Salim,712000002,,Groom's side,Vegetarian
            Bad Phone,12,Single,,
            Unknown Type,0712000004,VVIP,,
            Juma Again,+255712000001,Single,,
            ,0712000006,Single,,
            ,,,,
            """;

    @Test
    void checkingAFileReportsEachRowAndSavesNothing() throws Exception {
        TestCompany company = signUpNewCompany("Import Co");
        String token = company.ownerToken();
        String eventId = createEventAndGetId(token);

        uploadFile("/api/events/" + eventId + "/guests/import/check", token, "guests.csv", MIXED_CSV.getBytes())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.readyCount").value(2))
                .andExpect(jsonPath("$.readyExamples[0].phone").value("+255712000001"))
                .andExpect(jsonPath("$.readyExamples[1].cardType").value("Single"))
                .andExpect(jsonPath("$.problems.length()").value(3))
                .andExpect(jsonPath("$.problems[0].row").value(4))
                .andExpect(jsonPath("$.problems[1].message").value("Card type \"VVIP\" does not exist for this event"))
                .andExpect(jsonPath("$.problems[2].message").value("Name is missing"))
                .andExpect(jsonPath("$.duplicates[0].row").value(6));

        get("/api/events/" + eventId + "/guests", token).andExpect(jsonPath("$.totalGuests").value(0));
    }

    @Test
    void importingSavesOnlyTheGoodRows() throws Exception {
        TestCompany company = signUpNewCompany("Import Co");
        String token = company.ownerToken();
        String eventId = createEventAndGetId(token);

        uploadFile("/api/events/" + eventId + "/guests/import", token, "guests.csv", MIXED_CSV.getBytes())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.importedCount").value(2));

        get("/api/events/" + eventId, token)
                .andExpect(jsonPath("$.totalCards").value(2))
                .andExpect(jsonPath("$.totalSeats").value(3));

        // Uploading the same file again finds only duplicates
        uploadFile("/api/events/" + eventId + "/guests/import/check", token, "guests.csv", MIXED_CSV.getBytes())
                .andExpect(jsonPath("$.readyCount").value(0))
                .andExpect(jsonPath("$.duplicates.length()").value(3));
    }

    @Test
    void excelFilesWithSwahiliHeadingsAndNumberPhonesWork() throws Exception {
        TestCompany company = signUpNewCompany("Import Co");
        String token = company.ownerToken();
        String eventId = createEventAndGetId(token);

        byte[] excel = excelFile(workbook -> {
            Sheet sheet = workbook.createSheet("Wageni");
            Row headings = sheet.createRow(0);
            headings.createCell(0).setCellValue("Jina");
            headings.createCell(1).setCellValue("Namba ya simu");
            headings.createCell(2).setCellValue("Aina ya kadi");
            Row guest = sheet.createRow(1);
            guest.createCell(0).setCellValue("Bi. Rehema");
            guest.createCell(1).setCellValue(712000009);      // typed as a number: the leading 0 is lost
            guest.createCell(2).setCellValue("double");       // letter case does not matter
            Row fullNumber = sheet.createRow(2);
            fullNumber.createCell(0).setCellValue("Bw. Hamisi");
            fullNumber.createCell(1).setCellValue(255712000010L); // Excel would show this as 2.55712E+11
        });

        uploadFile("/api/events/" + eventId + "/guests/import", token, "wageni.xlsx", excel)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.importedCount").value(2))
                .andExpect(jsonPath("$.problems").isEmpty());

        get("/api/events/" + eventId + "/guests?search=Rehema", token)
                .andExpect(jsonPath("$.guests[0].phone").value("+255712000009"))
                .andExpect(jsonPath("$.guests[0].cardTypeName").value("Double"));
        get("/api/events/" + eventId + "/guests?search=Hamisi", token)
                .andExpect(jsonPath("$.guests[0].phone").value("+255712000010"));
    }

    @Test
    void unusableFilesAreRefusedWithAClearMessage() throws Exception {
        TestCompany company = signUpNewCompany("Import Co");
        String token = company.ownerToken();
        String eventId = createEventAndGetId(token);
        String checkAddress = "/api/events/" + eventId + "/guests/import/check";

        uploadFile(checkAddress, token, "guests.pdf", "not a guest list".getBytes())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Please upload an Excel (.xlsx) or CSV (.csv) file"));

        uploadFile(checkAddress, token, "guests.csv", "Name,Email\nAsha,asha@example.com\n".getBytes())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field").value("file"));

        uploadFile(checkAddress, token, "guests.xlsx", "this is not really excel".getBytes())
                .andExpect(status().isBadRequest());

        StringBuilder tooMany = new StringBuilder("Name,Phone\n");
        for (int number = 0; number <= GuestFileReader.MAX_GUEST_ROWS; number++) {
            tooMany.append("Guest ").append(number).append(",07").append(String.format("%08d", number)).append('\n');
        }
        uploadFile(checkAddress, token, "guests.csv", tooMany.toString().getBytes())
                .andExpect(status().isBadRequest());
    }

    @Test
    void theTemplateCanBeFilledInAndUploaded() throws Exception {
        TestCompany company = signUpNewCompany("Import Co");
        String token = company.ownerToken();
        String eventId = createEventAndGetId(token);

        byte[] template = get("/api/events/" + eventId + "/guests/import/template", token)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        // The template's example row imports as-is
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(template))) {
            assertThat(workbook.getSheet("Card types").getRow(2).getCell(0).getStringCellValue()).isEqualTo("Double");
        }
        uploadFile("/api/events/" + eventId + "/guests/import", token, "template.xlsx", template)
                .andExpect(jsonPath("$.importedCount").value(1));
    }

    @Test
    void checkInStaffCannotImport() throws Exception {
        TestCompany company = signUpNewCompany("Import Co");
        String checkInToken = addStaffMember(company, "CHECK_IN_STAFF");
        String eventId = createEventAndGetId(company.ownerToken());

        uploadFile("/api/events/" + eventId + "/guests/import", checkInToken, "guests.csv", MIXED_CSV.getBytes())
                .andExpect(status().isForbidden());
    }

    @Test
    void aCancelledEventsGuestListCannotBeChanged() throws Exception {
        TestCompany company = signUpNewCompany("Import Co");
        String token = company.ownerToken();
        String eventId = createEventAndGetId(token);
        put("/api/events/" + eventId + "/status", token, Map.of("status", "CANCELLED"));

        uploadFile("/api/events/" + eventId + "/guests/import", token, "guests.csv", MIXED_CSV.getBytes())
                .andExpect(status().isConflict());
    }

    private interface WorkbookFiller {
        void fill(Workbook workbook);
    }

    private static byte[] excelFile(WorkbookFiller filler) throws Exception {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream file = new ByteArrayOutputStream()) {
            filler.fill(workbook);
            workbook.write(file);
            return file.toByteArray();
        }
    }
}
