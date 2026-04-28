package com.expensetracker.service;

import com.expensetracker.domain.entity.Transaction;
import com.expensetracker.dto.response.TaxReportResponse;
import com.expensetracker.dto.response.TaxReportResponse.DeductibleCategory;
import com.expensetracker.dto.response.TaxReportResponse.TaxTransaction;
import com.expensetracker.repository.TransactionRepository;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TaxReportService {

    // Categories considered tax-deductible
    static final List<String> DEDUCTIBLE_CATEGORIES = List.of(
            "Healthcare", "Education", "Insurance"
    );

    private final TransactionRepository transactionRepository;

    public TaxReportResponse buildReport(UUID userId, int taxYear) {
        LocalDate from = LocalDate.of(taxYear, 1, 1);
        LocalDate to   = LocalDate.of(taxYear, 12, 31);

        List<Transaction> all = transactionRepository.findDebitsInRange(userId, from, to);

        Map<String, List<Transaction>> grouped = all.stream()
                .filter(tx -> tx.getCategory() != null
                        && isDeductible(tx.getCategory().getName()))
                .collect(Collectors.groupingBy(tx -> tx.getCategory().getName()));

        List<DeductibleCategory> categories = new ArrayList<>();
        for (String catName : DEDUCTIBLE_CATEGORIES) {
            List<Transaction> txList = grouped.getOrDefault(catName, Collections.emptyList());
            if (txList.isEmpty()) continue;

            // All transactions in this group share the same category entity
            Transaction first = txList.get(0);
            String icon  = first.getCategory().getIcon();
            String color = first.getCategory().getColor();

            BigDecimal total = txList.stream()
                    .map(Transaction::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            List<TaxTransaction> txDtos = txList.stream()
                    .sorted(Comparator.comparing(Transaction::getTransactionDate))
                    .map(tx -> new TaxTransaction(
                            tx.getTransactionDate().toString(),
                            tx.getDescription(),
                            tx.getMerchant() != null ? tx.getMerchant() : "",
                            tx.getAmount(),
                            tx.getCurrency()))
                    .toList();

            categories.add(new DeductibleCategory(catName, icon, color, total, txList.size(), txDtos));
        }

        BigDecimal totalDeductible = categories.stream()
                .map(DeductibleCategory::total)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new TaxReportResponse(taxYear, totalDeductible, categories);
    }

    // ── PDF export ────────────────────────────────────────────────
    public void exportPdf(UUID userId, int taxYear, OutputStream out) throws IOException {
        TaxReportResponse report = buildReport(userId, taxYear);

        PdfWriter pdfWriter = new PdfWriter(out);
        PdfDocument pdf = new PdfDocument(pdfWriter);
        Document doc = new Document(pdf);

        DeviceRgb headerBg   = new DeviceRgb(102, 126, 234);
        DeviceRgb sectionBg  = new DeviceRgb(240, 244, 255);
        DeviceRgb altRowBg   = new DeviceRgb(248, 249, 250);
        DeviceRgb totalBg    = new DeviceRgb(220, 230, 255);

        doc.add(new Paragraph("Tax Deduction Report — " + report.taxYear())
                .setFontSize(20).setBold()
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(4));
        doc.add(new Paragraph("Financial Year: 01 Jan " + report.taxYear() + " – 31 Dec " + report.taxYear())
                .setFontSize(10).setFontColor(ColorConstants.GRAY)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(4));
        doc.add(new Paragraph("Deductible Categories: Healthcare · Education · Insurance")
                .setFontSize(9).setFontColor(ColorConstants.GRAY)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(20));

        // Summary box
        Table summaryTable = new Table(UnitValue.createPercentArray(new float[]{4f, 2f, 2f}))
                .useAllAvailableWidth()
                .setMarginBottom(24);
        for (String h : new String[]{"Category", "Transactions", "Total Amount"}) {
            summaryTable.addHeaderCell(new com.itextpdf.layout.element.Cell()
                    .add(new Paragraph(h).setBold().setFontColor(ColorConstants.WHITE).setFontSize(9))
                    .setBackgroundColor(headerBg).setPadding(6));
        }
        int rowIdx = 0;
        for (DeductibleCategory cat : report.categories()) {
            DeviceRgb bg = rowIdx++ % 2 == 0 ? new DeviceRgb(255,255,255) : altRowBg;
            summaryTable.addCell(new com.itextpdf.layout.element.Cell().add(new Paragraph(cat.name()).setFontSize(9)).setBackgroundColor(bg).setPadding(5));
            summaryTable.addCell(new com.itextpdf.layout.element.Cell().add(new Paragraph(String.valueOf(cat.transactionCount())).setFontSize(9).setTextAlignment(TextAlignment.CENTER)).setBackgroundColor(bg).setPadding(5));
            summaryTable.addCell(new com.itextpdf.layout.element.Cell().add(new Paragraph(formatAmount(cat.total())).setFontSize(9).setTextAlignment(TextAlignment.RIGHT)).setBackgroundColor(bg).setPadding(5));
        }
        // Total row
        summaryTable.addCell(new com.itextpdf.layout.element.Cell().add(new Paragraph("TOTAL DEDUCTIBLE").setBold().setFontSize(9)).setBackgroundColor(totalBg).setPadding(6));
        summaryTable.addCell(new com.itextpdf.layout.element.Cell().add(new Paragraph("").setFontSize(9)).setBackgroundColor(totalBg).setPadding(6));
        summaryTable.addCell(new com.itextpdf.layout.element.Cell().add(new Paragraph(formatAmount(report.totalDeductible())).setBold().setFontSize(9).setTextAlignment(TextAlignment.RIGHT)).setBackgroundColor(totalBg).setPadding(6));
        doc.add(summaryTable);

        // Per-category detail tables
        for (DeductibleCategory cat : report.categories()) {
            doc.add(new Paragraph(cat.name() + "  (" + cat.transactionCount() + " transactions)")
                    .setFontSize(12).setBold()
                    .setBackgroundColor(sectionBg)
                    .setPadding(8)
                    .setMarginBottom(4));

            float[] cols = {2f, 4f, 3f, 2f};
            Table txTable = new Table(UnitValue.createPercentArray(cols)).useAllAvailableWidth().setMarginBottom(20);
            for (String h : new String[]{"Date", "Description", "Merchant", "Amount"}) {
                txTable.addHeaderCell(new com.itextpdf.layout.element.Cell()
                        .add(new Paragraph(h).setBold().setFontColor(ColorConstants.WHITE).setFontSize(8))
                        .setBackgroundColor(headerBg).setPadding(5));
            }
            int ri = 0;
            for (TaxTransaction tx : cat.transactions()) {
                DeviceRgb bg = ri++ % 2 == 0 ? new DeviceRgb(255,255,255) : altRowBg;
                txTable.addCell(new com.itextpdf.layout.element.Cell().add(new Paragraph(tx.date()).setFontSize(8)).setBackgroundColor(bg).setPadding(4));
                txTable.addCell(new com.itextpdf.layout.element.Cell().add(new Paragraph(tx.description()).setFontSize(8)).setBackgroundColor(bg).setPadding(4));
                txTable.addCell(new com.itextpdf.layout.element.Cell().add(new Paragraph(tx.merchant()).setFontSize(8)).setBackgroundColor(bg).setPadding(4));
                txTable.addCell(new com.itextpdf.layout.element.Cell().add(new Paragraph(tx.currency() + " " + String.format("%,.2f", tx.amount())).setFontSize(8).setTextAlignment(TextAlignment.RIGHT)).setBackgroundColor(bg).setPadding(4));
            }
            doc.add(txTable);
        }

        doc.add(new Paragraph("This report is generated for tax-filing purposes. Please verify with your tax advisor.")
                .setFontSize(8).setFontColor(ColorConstants.GRAY)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginTop(16));

        doc.close();
    }

    // ── Excel export ──────────────────────────────────────────────
    public void exportExcel(UUID userId, int taxYear, OutputStream out) throws IOException {
        TaxReportResponse report = buildReport(userId, taxYear);

        try (XSSFWorkbook wb = new XSSFWorkbook()) {

            // ── Summary sheet ────────────────────────────────────
            Sheet summary = wb.createSheet("Summary");
            DataFormat fmt = wb.createDataFormat();

            CellStyle titleStyle = wb.createCellStyle();
            Font titleFont = wb.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 14);
            titleStyle.setFont(titleFont);

            CellStyle headerStyle = wb.createCellStyle();
            headerStyle.setFillForegroundColor(IndexedColors.INDIGO.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            Font hFont = wb.createFont();
            hFont.setBold(true);
            hFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(hFont);
            headerStyle.setBorderBottom(BorderStyle.THIN);

            CellStyle totalStyle = wb.createCellStyle();
            totalStyle.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
            totalStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            totalStyle.setDataFormat(fmt.getFormat("#,##0.00"));
            Font totalFont = wb.createFont();
            totalFont.setBold(true);
            totalStyle.setFont(totalFont);

            CellStyle amtStyle = wb.createCellStyle();
            amtStyle.setDataFormat(fmt.getFormat("#,##0.00"));

            Row titleRow = summary.createRow(0);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("Tax Deduction Report — Financial Year " + taxYear);
            titleCell.setCellStyle(titleStyle);
            summary.addMergedRegion(new CellRangeAddress(0, 0, 0, 3));

            Row subRow = summary.createRow(1);
            subRow.createCell(0).setCellValue("Deductible categories: Healthcare, Education, Insurance");
            summary.addMergedRegion(new CellRangeAddress(1, 1, 0, 3));

            Row hRow = summary.createRow(3);
            for (int i = 0; i < 4; i++) {
                Cell c = hRow.createCell(i);
                c.setCellStyle(headerStyle);
            }
            hRow.getCell(0).setCellValue("Category");
            hRow.getCell(1).setCellValue("Transactions");
            hRow.getCell(2).setCellValue("Total (INR)");
            hRow.getCell(3).setCellValue("% of Total");

            int r = 4;
            for (DeductibleCategory cat : report.categories()) {
                Row row = summary.createRow(r++);
                row.createCell(0).setCellValue(cat.name());
                row.createCell(1).setCellValue(cat.transactionCount());
                Cell amtCell = row.createCell(2);
                amtCell.setCellValue(cat.total().doubleValue());
                amtCell.setCellStyle(amtStyle);
                double pct = report.totalDeductible().compareTo(BigDecimal.ZERO) == 0 ? 0
                        : cat.total().doubleValue() * 100.0 / report.totalDeductible().doubleValue();
                row.createCell(3).setCellValue(String.format("%.1f%%", pct));
            }

            Row totRow = summary.createRow(r);
            totRow.createCell(0).setCellValue("TOTAL DEDUCTIBLE");
            totRow.getCell(0).setCellStyle(totalStyle);
            totRow.createCell(1).setCellValue("");
            totRow.getCell(1).setCellStyle(totalStyle);
            Cell totAmt = totRow.createCell(2);
            totAmt.setCellValue(report.totalDeductible().doubleValue());
            totAmt.setCellStyle(totalStyle);
            totRow.createCell(3).setCellValue("100%");
            totRow.getCell(3).setCellStyle(totalStyle);

            for (int i = 0; i < 4; i++) summary.autoSizeColumn(i);

            // ── Per-category detail sheets ───────────────────────
            CellStyle debitStyle = wb.createCellStyle();
            debitStyle.setDataFormat(fmt.getFormat("#,##0.00"));
            debitStyle.setFillForegroundColor(IndexedColors.ROSE.getIndex());
            debitStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            for (DeductibleCategory cat : report.categories()) {
                Sheet sheet = wb.createSheet(cat.name());
                String[] headers = {"Date", "Description", "Merchant", "Amount", "Currency"};
                Row hr = sheet.createRow(0);
                for (int i = 0; i < headers.length; i++) {
                    Cell c = hr.createCell(i);
                    c.setCellValue(headers[i]);
                    c.setCellStyle(headerStyle);
                }
                int dr = 1;
                for (TaxTransaction tx : cat.transactions()) {
                    Row row = sheet.createRow(dr++);
                    row.createCell(0).setCellValue(tx.date());
                    row.createCell(1).setCellValue(tx.description());
                    row.createCell(2).setCellValue(tx.merchant());
                    Cell ac = row.createCell(3);
                    ac.setCellValue(tx.amount().doubleValue());
                    ac.setCellStyle(debitStyle);
                    row.createCell(4).setCellValue(tx.currency());
                }
                Row catTotRow = sheet.createRow(dr + 1);
                catTotRow.createCell(0).setCellValue("Category Total");
                catTotRow.getCell(0).setCellStyle(totalStyle);
                Cell catTotAmt = catTotRow.createCell(3);
                catTotAmt.setCellValue(cat.total().doubleValue());
                catTotAmt.setCellStyle(totalStyle);
                for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);
            }

            wb.write(out);
        }
    }

    private boolean isDeductible(String categoryName) {
        return DEDUCTIBLE_CATEGORIES.stream()
                .anyMatch(d -> d.equalsIgnoreCase(categoryName));
    }

    private String formatAmount(BigDecimal amount) {
        return String.format("%,.2f", amount);
    }
}
