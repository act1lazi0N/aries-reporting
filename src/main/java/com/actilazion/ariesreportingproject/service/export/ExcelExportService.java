package com.actilazion.ariesreportingproject.service.export;

import com.actilazion.ariesreportingproject.dto.response.AccountStatementResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;

/**
 * Generate Excel (.xlsx) from AccountStatementResponse.
 * Using Apache POI XSSFWorkbook — streaming-safe for large files.
 */
@Slf4j
@Service
public class ExcelExportService {
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    public void generateStatement(AccountStatementResponse statement, Path outputPath) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Account Statement");

            // Column widths
            sheet.setColumnWidth(0, 20 * 256);  // Date
            sheet.setColumnWidth(1, 18 * 256);  // From Account
            sheet.setColumnWidth(2, 18 * 256);  // To Account
            sheet.setColumnWidth(3, 20 * 256);  // From Name
            sheet.setColumnWidth(4, 20 * 256);  // To Name
            sheet.setColumnWidth(5, 15 * 256);  // Amount
            sheet.setColumnWidth(6, 10 * 256);  // Currency
            sheet.setColumnWidth(7, 12 * 256);  // Status
            sheet.setColumnWidth(8, 30 * 256);  // Description

            // Style
            CellStyle titleStyle = createTitleStyle(workbook);
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle amountStyle = createAmountStyle(workbook);
            CellStyle dateStyle = createDateStyle(workbook);

            int rowNum = 0;

            // Title
            Row titleRow = sheet.createRow(rowNum++);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("ACCOUNT STATEMENT");
            titleCell.setCellStyle(titleStyle);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 8));

            // Period info
            Row periodRow = sheet.createRow(rowNum++);
            periodRow.createCell(0).setCellValue(
                    "Period: " + statement.periodFrom() + " → " + statement.periodTo());
            sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, 4));

            rowNum++; // blank row

            // Summary
            Row summaryHeader = sheet.createRow(rowNum++);
            setCellWithStyle(summaryHeader, 0, "Total Debit", headerStyle);
            setCellWithStyle(summaryHeader, 1, "Total Credit", headerStyle);
            setCellWithStyle(summaryHeader, 2, "Net Flow", headerStyle);

            Row summaryData = sheet.createRow(rowNum++);
            summaryData.createCell(0).setCellValue(statement.totalDebit().doubleValue());
            summaryData.createCell(1).setCellValue(statement.totalCredit().doubleValue());
            summaryData.createCell(2).setCellValue(statement.netFlow().doubleValue());
            for (int i = 0; i <= 2; i++) {
                summaryData.getCell(i).setCellStyle(amountStyle);
            }
            rowNum++;

            // Transaction header
            Row header = sheet.createRow(rowNum++);
            String[] headers = {
                    "Date", "From Account", "To Account",
                    "From Name", "To Name",
                    "Amount", "Currency", "Status", "Description"
            };
            for (int i = 0; i < headers.length; i++) {
                setCellWithStyle(header, i, headers[i], headerStyle);
            }

            // Freeze header row
            sheet.createFreezePane(0, rowNum - 1);

            // Transaction rows
            if (statement.transactions() != null) {
                for (AccountStatementResponse.TransactionLine tx : statement.transactions().getContent()) {
                    Row row = sheet.createRow(rowNum++);

                    Cell dateCell = row.createCell(0);
                    dateCell.setCellValue(
                            tx.createdAt().format(DATE_FMT));
                    dateCell.setCellStyle(dateStyle);

                    row.createCell(1).setCellValue(tx.fromAccountNumber());
                    row.createCell(2).setCellValue(tx.toAccountNumber());
                    row.createCell(3).setCellValue(tx.fromOwnerName());
                    row.createCell(4).setCellValue(tx.toOwnerName());

                    Cell amtCell = row.createCell(5);
                    amtCell.setCellValue(tx.amount().doubleValue());
                    amtCell.setCellStyle(amountStyle);

                    row.createCell(6).setCellValue(tx.currency());
                    row.createCell(7).setCellValue(tx.status());
                    row.createCell(8).setCellValue(
                            tx.description() != null ? tx.description() : "");
                }
            }

            // Write to file
            try (FileOutputStream fos = new FileOutputStream(outputPath.toFile())) {
                workbook.write(fos);
            }

            log.info("[EXCEL] Generated {} rows at {}", rowNum, outputPath);
        }
    }
    // Style factories
    private CellStyle createTitleStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 14);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    private CellStyle createHeaderStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        return style;
    }

    private CellStyle createAmountStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        DataFormat fmt  = wb.createDataFormat();
        style.setDataFormat(fmt.getFormat("#,##0.00"));
        style.setAlignment(HorizontalAlignment.RIGHT);
        return style;
    }

    private CellStyle createDateStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    private void setCellWithStyle(Row row, int col, String value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }
}
