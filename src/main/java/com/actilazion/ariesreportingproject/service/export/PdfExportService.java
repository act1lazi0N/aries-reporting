package com.actilazion.ariesreportingproject.service.export;

import com.actilazion.ariesreportingproject.dto.response.AccountStatementResponse;

import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.*;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;

/**
 * Generate a PDF statement by using iText8.
 * Layout: header -> summary -> transaction table -> footer
 */
@Slf4j
@Service
public class PdfExportService {
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    // Brand color
    private static final DeviceRgb HEADER_BG = new DeviceRgb(15, 23, 42);     // dark blue
    private static final DeviceRgb ACCENT = new DeviceRgb(99, 102, 241);   // indigo

    public void generateStatement(AccountStatementResponse statement, Path outputPath) throws IOException {
        try (PdfDocument pdf = new PdfDocument(new PdfWriter(outputPath.toFile())); Document doc = new Document(pdf, PageSize.A4)) {
            doc.setMargins(36, 36, 36, 36);
            PdfFont bold = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
            PdfFont regular = PdfFontFactory.createFont(StandardFonts.HELVETICA);

            // Header
            Table headerTable = new Table(
                    UnitValue.createPercentArray(new float[]{70, 30}))
                    .useAllAvailableWidth();

            Cell titleCell = new Cell()
                    .add(new Paragraph("ACCOUNT STATEMENT")
                            .setFont(bold).setFontSize(18)
                            .setFontColor(ColorConstants.WHITE))
                    .add(new Paragraph("Aries Transfer System")
                            .setFont(regular).setFontSize(10)
                            .setFontColor(ColorConstants.LIGHT_GRAY))
                    .setBackgroundColor(HEADER_BG)
                    .setPadding(12)
                    .setBorder(null);

            Cell periodCell = new Cell()
                    .add(new Paragraph("Period")
                            .setFont(bold).setFontSize(9)
                            .setFontColor(ColorConstants.LIGHT_GRAY))
                    .add(new Paragraph(
                            statement.periodFrom() + " → " + statement.periodTo())
                            .setFont(bold).setFontSize(11)
                            .setFontColor(ColorConstants.WHITE))
                    .setBackgroundColor(HEADER_BG)
                    .setPadding(12)
                    .setTextAlignment(TextAlignment.RIGHT)
                    .setBorder(null);

            headerTable.addCell(titleCell);
            headerTable.addCell(periodCell);
            doc.add(headerTable);
            doc.add(new Paragraph(" "));

            // Summary cards
            Table summary = new Table(
                    UnitValue.createPercentArray(new float[]{33, 33, 34}))
                    .useAllAvailableWidth();

            addSummaryCard(summary, bold, regular,
                    "Total Debit",   statement.totalDebit(),   "VND");
            addSummaryCard(summary, bold, regular,
                    "Total Credit",  statement.totalCredit(),  "VND");
            addSummaryCard(summary, bold, regular,
                    "Net Flow",      statement.netFlow(),      "VND");

            doc.add(summary);
            doc.add(new Paragraph(" "));

            // Transaction table
            doc.add(new Paragraph("Transactions")
                    .setFont(bold).setFontSize(12)
                    .setFontColor(new DeviceRgb(15, 23, 42)));

            Table txTable = new Table(
                    UnitValue.createPercentArray(
                            new float[]{18, 14, 14, 14, 12, 8, 10, 10}))
                    .useAllAvailableWidth()
                    .setFontSize(8);

            // Table header
            String[] cols = {
                    "Date", "From Acc", "To Acc",
                    "From", "To", "Amount", "Status", "Desc"
            };
            for (String col : cols) {
                txTable.addHeaderCell(
                        new Cell().add(new Paragraph(col).setFont(bold))
                                .setBackgroundColor(ACCENT)
                                .setFontColor(ColorConstants.WHITE)
                                .setPadding(4)
                                .setBorder(new SolidBorder(ColorConstants.WHITE, 1)));
            }
            // Table rows
            if (statement.transactions() != null) {
                boolean alternate = false;
                for (AccountStatementResponse.TransactionLine tx
                        : statement.transactions().getContent()) {

                    DeviceRgb rowBg = alternate
                            ? new DeviceRgb(248, 250, 252)
                            : new DeviceRgb(255, 255, 255);

                    String[] values = {
                            tx.createdAt().format(DATE_FMT),
                            tx.fromAccountNumber(),
                            tx.toAccountNumber(),
                            tx.fromOwnerName(),
                            tx.toOwnerName(),
                            formatAmount(tx.amount()),
                            tx.status(),
                            tx.description() != null ? tx.description() : ""
                    };

                    for (String val : values) {
                        txTable.addCell(
                                new Cell()
                                        .add(new Paragraph(val).setFont(regular))
                                        .setBackgroundColor(rowBg)
                                        .setPadding(3));
                    }
                    alternate = !alternate;
                }
            }

            doc.add(txTable);

            // Footer
            doc.add(new Paragraph(" "));
            doc.add(new Paragraph(
                    "Generated by Aries Reporting System — "
                            + java.time.LocalDateTime.now().format(
                            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")))
                    .setFont(regular).setFontSize(8)
                    .setFontColor(ColorConstants.LIGHT_GRAY)
                    .setTextAlignment(TextAlignment.CENTER));

            log.info("[PDF] Generated statement at {}", outputPath);
        }
    }
    private void addSummaryCard(Table table, PdfFont bold, PdfFont regular,
                                String label, BigDecimal value, String currency) {
        table.addCell(
                new Cell()
                        .add(new Paragraph(label)
                                .setFont(regular).setFontSize(9)
                                .setFontColor(ColorConstants.GRAY))
                        .add(new Paragraph(formatAmount(value) + " " + currency)
                                .setFont(bold).setFontSize(13))
                        .setBorderBottom(new com.itextpdf.layout.borders.SolidBorder(ACCENT, 2))
                        .setPadding(8));
    }

    private String formatAmount(BigDecimal amount) {
        return String.format("%,.2f", amount);
    }
}
