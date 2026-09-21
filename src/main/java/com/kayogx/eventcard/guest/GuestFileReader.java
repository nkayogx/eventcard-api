package com.kayogx.eventcard.guest;

import com.kayogx.eventcard.common.InvalidInputException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Reads a guest list from an Excel (.xlsx) or CSV file.
 *
 * The first row must hold the column headings. We recognise common headings in
 * English and Swahili ("Phone", "Simu", "Namba ya simu"...), ignoring letter case,
 * spaces and punctuation. Only the Name and Phone columns are required.
 *
 * This class only READS the file - checking the values is done by GuestImportService.
 */
@Component
public class GuestFileReader {

    public static final int MAX_GUEST_ROWS = 5000;

    // Accepted headings for each column, written without spaces or punctuation
    private static final Set<String> NAME_HEADINGS = Set.of("name", "nameoncard", "guest", "guestname", "fullname", "jina");
    private static final Set<String> PHONE_HEADINGS = Set.of("phone", "phonenumber", "mobile", "whatsapp", "telephone",
            "simu", "namba", "nambayasimu");
    private static final Set<String> CARD_TYPE_HEADINGS = Set.of("cardtype", "type", "card", "category", "aina", "ainayakadi");
    private static final Set<String> GROUP_HEADINGS = Set.of("group", "side", "kundi", "upande");
    private static final Set<String> NOTES_HEADINGS = Set.of("notes", "note", "comments", "maelezo");

    /** One guest row from the file, exactly as typed (not checked yet). {@code rowNumber} is the row in the file. */
    public record FileRow(int rowNumber, String name, String phone, String cardType, String group, String notes) {
    }

    /** Where each column is in the file (-1 means the file does not have that column). */
    private record ColumnPositions(int name, int phone, int cardType, int group, int notes) {
    }

    public List<FileRow> read(MultipartFile file) {
        List<List<String>> allRows = readAllRowsAsText(file);
        if (allRows.isEmpty()) {
            throw new InvalidInputException("The file is empty", "file");
        }

        ColumnPositions columns = findColumns(allRows.get(0));
        List<FileRow> guestRows = new ArrayList<>();
        for (int index = 1; index < allRows.size(); index++) {
            List<String> cells = allRows.get(index);
            if (cells.stream().allMatch(String::isBlank)) {
                continue; // completely empty rows are ignored
            }
            int rowNumberInFile = index + 1; // row 1 is the headings
            guestRows.add(new FileRow(rowNumberInFile,
                    cell(cells, columns.name()), cell(cells, columns.phone()), cell(cells, columns.cardType()),
                    cell(cells, columns.group()), cell(cells, columns.notes())));
        }

        if (guestRows.size() > MAX_GUEST_ROWS) {
            throw new InvalidInputException("The file has " + guestRows.size() + " guests. The maximum is "
                    + MAX_GUEST_ROWS + " per upload - please split it into smaller files.", "file");
        }
        return guestRows;
    }

    // ---------- Finding the columns ----------

    private static ColumnPositions findColumns(List<String> headings) {
        int name = findColumn(headings, NAME_HEADINGS);
        int phone = findColumn(headings, PHONE_HEADINGS);
        if (name == -1 || phone == -1) {
            throw new InvalidInputException(
                    "The first row must contain the column headings, including \"Name\" and \"Phone\". "
                            + "Tip: download the template to see the expected layout.", "file");
        }
        return new ColumnPositions(name, phone,
                findColumn(headings, CARD_TYPE_HEADINGS),
                findColumn(headings, GROUP_HEADINGS),
                findColumn(headings, NOTES_HEADINGS));
    }

    private static int findColumn(List<String> headings, Set<String> acceptedHeadings) {
        for (int position = 0; position < headings.size(); position++) {
            String simplified = headings.get(position).toLowerCase().replaceAll("[^a-z]", "");
            if (acceptedHeadings.contains(simplified)) {
                return position;
            }
        }
        return -1;
    }

    private static String cell(List<String> cells, int position) {
        if (position == -1 || position >= cells.size()) {
            return "";
        }
        return cells.get(position).trim();
    }

    // ---------- Reading the file into rows of text ----------

    private static List<List<String>> readAllRowsAsText(MultipartFile file) {
        String fileName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        try {
            if (fileName.endsWith(".xlsx") || fileName.endsWith(".xls")) {
                return readExcel(file.getInputStream());
            }
            if (fileName.endsWith(".csv")) {
                return readCsv(new String(file.getBytes(), StandardCharsets.UTF_8));
            }
        } catch (IOException | RuntimeException unreadable) {
            throw new InvalidInputException("We could not read this file. Please check it opens correctly in Excel.", "file");
        }
        throw new InvalidInputException("Please upload an Excel (.xlsx) or CSV (.csv) file", "file");
    }

    private static List<List<String>> readExcel(InputStream content) throws IOException {
        List<List<String>> rows = new ArrayList<>();
        try (Workbook workbook = WorkbookFactory.create(content)) {
            Sheet firstSheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();
            FormulaEvaluator formulas = workbook.getCreationHelper().createFormulaEvaluator();

            for (int rowIndex = 0; rowIndex <= firstSheet.getLastRowNum(); rowIndex++) {
                Row row = firstSheet.getRow(rowIndex);
                List<String> cells = new ArrayList<>();
                if (row != null) {
                    for (int cellIndex = 0; cellIndex < row.getLastCellNum(); cellIndex++) {
                        cells.add(excelCellAsText(row.getCell(cellIndex), formatter, formulas));
                    }
                }
                rows.add(cells);
            }
        }
        return rows;
    }

    /**
     * Excel stores phone numbers typed as numbers (e.g. 255712345678) and may show them
     * as "2.55712E+11". For whole numbers we therefore write out every digit ourselves.
     */
    private static String excelCellAsText(Cell cell, DataFormatter formatter, FormulaEvaluator formulas) {
        if (cell == null) {
            return "";
        }
        if (cell.getCellType() == CellType.NUMERIC && !DateUtil.isCellDateFormatted(cell)) {
            double number = cell.getNumericCellValue();
            if (number == Math.floor(number)) {
                return new BigDecimal(number).toBigInteger().toString();
            }
        }
        return formatter.formatCellValue(cell, formulas);
    }

    private static List<List<String>> readCsv(String text) throws IOException {
        // Files saved by Excel often start with an invisible "byte order mark" - remove it
        if (text.startsWith("﻿")) {
            text = text.substring(1);
        }
        // Some Excel versions separate columns with ";" instead of ","
        String firstLine = text.lines().findFirst().orElse("");
        char separator = firstLine.contains(";") && !firstLine.contains(",") ? ';' : ',';

        List<List<String>> rows = new ArrayList<>();
        CSVFormat format = CSVFormat.DEFAULT.builder().setDelimiter(separator).get();
        try (CSVParser parser = CSVParser.parse(new StringReader(text), format)) {
            for (CSVRecord record : parser) {
                rows.add(record.toList());
            }
        }
        return rows;
    }
}
