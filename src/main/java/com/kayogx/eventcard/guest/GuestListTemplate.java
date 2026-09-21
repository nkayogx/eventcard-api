package com.kayogx.eventcard.guest;

import com.kayogx.eventcard.event.CardType;
import com.kayogx.eventcard.event.CardTypeRepository;
import com.kayogx.eventcard.event.EventFinder;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.UUID;

/**
 * Builds an Excel file that vendors can fill in and upload.
 * Sheet 1 "Guests" has the right column headings and one example row.
 * Sheet 2 "Card types" lists this event's card types, so vendors know what to type.
 */
@Component
public class GuestListTemplate {

    private final CardTypeRepository cardTypeRepository;
    private final EventFinder eventFinder;

    public GuestListTemplate(CardTypeRepository cardTypeRepository, EventFinder eventFinder) {
        this.cardTypeRepository = cardTypeRepository;
        this.eventFinder = eventFinder;
    }

    @Transactional(readOnly = true)
    public byte[] createFor(UUID eventId) {
        eventFinder.findEvent(eventId);
        List<CardType> cardTypes = cardTypeRepository.findByEventIdOrderBySortOrderAsc(eventId);

        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream file = new ByteArrayOutputStream()) {
            CellStyle bold = workbook.createCellStyle();
            Font boldFont = workbook.createFont();
            boldFont.setBold(true);
            bold.setFont(boldFont);

            // Store phones as text, so Excel keeps the leading 0 of numbers like 0712345678
            CellStyle text = workbook.createCellStyle();
            text.setDataFormat(workbook.createDataFormat().getFormat("@"));

            Sheet guests = workbook.createSheet("Guests");
            writeRow(guests, 0, bold, "Name", "Phone", "Card type", "Group", "Notes");
            String exampleCardType = cardTypes.size() > 1 ? cardTypes.get(1).getName() : cardTypes.get(0).getName();
            writeRow(guests, 1, null, "Mr & Mrs Juma", "0712345678", exampleCardType, "Bride's side", "Example - replace me");
            guests.setDefaultColumnStyle(1, text);
            guests.getRow(1).getCell(1).setCellStyle(text);
            for (int column = 0; column < 5; column++) {
                guests.setColumnWidth(column, 22 * 256);
            }

            Sheet cardTypeSheet = workbook.createSheet("Card types");
            writeRow(cardTypeSheet, 0, bold, "Card type", "Seats");
            for (int index = 0; index < cardTypes.size(); index++) {
                CardType cardType = cardTypes.get(index);
                writeRow(cardTypeSheet, index + 1, null, cardType.getName(), String.valueOf(cardType.getSeats()));
            }
            cardTypeSheet.setColumnWidth(0, 22 * 256);

            workbook.write(file);
            return file.toByteArray();
        } catch (IOException problem) {
            throw new UncheckedIOException("Could not create the template", problem);
        }
    }

    private static void writeRow(Sheet sheet, int rowIndex, CellStyle style, String... values) {
        Row row = sheet.createRow(rowIndex);
        for (int column = 0; column < values.length; column++) {
            Cell cell = row.createCell(column);
            cell.setCellValue(values[column]);
            if (style != null) {
                cell.setCellStyle(style);
            }
        }
    }
}
