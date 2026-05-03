package com.expensetracker.service;

import com.expensetracker.domain.entity.Category;
import com.expensetracker.domain.entity.Transaction;
import com.expensetracker.domain.enums.TransactionType;
import com.expensetracker.dto.response.TransactionResponse;
import com.expensetracker.exception.ResourceNotFoundException;
import com.expensetracker.repository.CategoryRepository;
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
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;

    @Transactional(readOnly = true)
    public Page<TransactionResponse> getTransactions(
            UUID userId, LocalDate from, LocalDate to,
            String search, Integer categoryId, TransactionType txType,
            Pageable pageable) {
        return transactionRepository
                .search(userId, from, to, search, categoryId, txType == null ? null : txType.name(), pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public void exportCsv(UUID userId, LocalDate from, LocalDate to,
                          String search, Integer categoryId, TransactionType txType,
                          PrintWriter writer) {
        List<Transaction> transactions = transactionRepository.searchAll(
                userId, from, to, search, categoryId, txType == null ? null : txType.name());

        writer.println("Date,Description,Merchant,Category,Type,Amount,Currency");
        for (Transaction tx : transactions) {
            writer.printf("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"%n",
                    tx.getTransactionDate(),
                    escape(tx.getDescription()),
                    tx.getMerchant() != null ? escape(tx.getMerchant()) : "",
                    tx.getCategory() != null ? escape(tx.getCategory().getName()) : "Uncategorized",
                    tx.getTransactionType(),
                    tx.getAmount(),
                    tx.getCurrency());
        }
        writer.flush();
    }

    @Transactional(readOnly = true)
    public void exportExcel(UUID userId, LocalDate from, LocalDate to,
                            String search, Integer categoryId, TransactionType txType,
                            OutputStream out) throws IOException {
        List<Transaction> transactions = transactionRepository.searchAll(
                userId, from, to, search, categoryId, txType == null ? null : txType.name());

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Transactions");

            // Header style
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFillForegroundColor(IndexedColors.INDIGO.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);

            // Amount styles
            CellStyle debitStyle = workbook.createCellStyle();
            DataFormat fmt = workbook.createDataFormat();
            debitStyle.setDataFormat(fmt.getFormat("#,##0.00"));
            debitStyle.setFillForegroundColor(IndexedColors.ROSE.getIndex());
            debitStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            CellStyle creditStyle = workbook.createCellStyle();
            creditStyle.setDataFormat(fmt.getFormat("#,##0.00"));
            creditStyle.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
            creditStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            String[] headers = {"Date", "Description", "Merchant", "Category", "Type", "Amount", "Currency"};
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowIdx = 1;
            for (Transaction tx : transactions) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(tx.getTransactionDate().toString());
                row.createCell(1).setCellValue(tx.getDescription());
                row.createCell(2).setCellValue(tx.getMerchant() != null ? tx.getMerchant() : "");
                row.createCell(3).setCellValue(tx.getCategory() != null ? tx.getCategory().getName() : "Uncategorized");
                row.createCell(4).setCellValue(tx.getTransactionType().name());
                Cell amtCell = row.createCell(5);
                amtCell.setCellValue(tx.getAmount().doubleValue());
                amtCell.setCellStyle(tx.getTransactionType() == TransactionType.DEBIT ? debitStyle : creditStyle);
                row.createCell(6).setCellValue(tx.getCurrency());
            }

            for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);
            workbook.write(out);
        }
    }

    @Transactional(readOnly = true)
    public void exportPdf(UUID userId, LocalDate from, LocalDate to,
                          String search, Integer categoryId, TransactionType txType,
                          OutputStream out) throws IOException {
        List<Transaction> transactions = transactionRepository.searchAll(
                userId, from, to, search, categoryId, txType == null ? null : txType.name());

        PdfWriter pdfWriter = new PdfWriter(out);
        PdfDocument pdf = new PdfDocument(pdfWriter);
        Document document = new Document(pdf);

        document.add(new Paragraph("Transaction Report")
                .setFontSize(18).setBold()
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(16));

        if (from != null || to != null) {
            String range = (from != null ? from.toString() : "start") + " → " + (to != null ? to.toString() : "today");
            document.add(new Paragraph("Period: " + range)
                    .setFontSize(10).setFontColor(ColorConstants.GRAY)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginBottom(16));
        }

        float[] colWidths = {2f, 4f, 3f, 2.5f, 1.5f, 2f, 1.5f};
        Table table = new Table(UnitValue.createPercentArray(colWidths)).useAllAvailableWidth();

        DeviceRgb headerBg = new DeviceRgb(102, 126, 234);
        String[] headers = {"Date", "Description", "Merchant", "Category", "Type", "Amount", "Currency"};
        for (String h : headers) {
            table.addHeaderCell(new com.itextpdf.layout.element.Cell()
                    .add(new Paragraph(h).setBold().setFontColor(ColorConstants.WHITE).setFontSize(9))
                    .setBackgroundColor(headerBg)
                    .setPadding(6));
        }

        DeviceRgb debitBg  = new DeviceRgb(255, 235, 235);
        DeviceRgb creditBg = new DeviceRgb(235, 255, 235);
        DeviceRgb altBg    = new DeviceRgb(248, 249, 250);
        DeviceRgb whiteBg  = new DeviceRgb(255, 255, 255);

        int rowIdx = 0;
        for (Transaction tx : transactions) {
            boolean isDebit = tx.getTransactionType() == TransactionType.DEBIT;
            DeviceRgb rowBg = rowIdx % 2 == 0 ? whiteBg : altBg;
            String[] cells = {
                tx.getTransactionDate().toString(),
                tx.getDescription(),
                tx.getMerchant() != null ? tx.getMerchant() : "",
                tx.getCategory() != null ? tx.getCategory().getName() : "Uncategorized",
                tx.getTransactionType().name(),
                tx.getCurrency() + " " + String.format("%,.2f", tx.getAmount()),
                tx.getCurrency()
            };
            for (int i = 0; i < cells.length; i++) {
                DeviceRgb bg = (i == 5) ? (isDebit ? debitBg : creditBg) : rowBg;
                table.addCell(new com.itextpdf.layout.element.Cell()
                        .add(new Paragraph(cells[i]).setFontSize(8))
                        .setBackgroundColor(bg)
                        .setPadding(5));
            }
            rowIdx++;
        }

        document.add(table);
        document.add(new Paragraph("Total transactions: " + transactions.size())
                .setFontSize(9).setFontColor(ColorConstants.GRAY).setMarginTop(12));
        document.close();
    }

    @Transactional
    public TransactionResponse updateCategory(UUID transactionId, Integer categoryId, String note, UUID userId) {
        Transaction tx = transactionRepository.findByIdAndUserId(transactionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found: " + transactionId));
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + categoryId));
        tx.setCategory(category);
        tx.setCategorizationNote(note);
        tx.setAiCategorized(false);
        return toResponse(transactionRepository.save(tx));
    }

    @Transactional
    public void deleteTransaction(UUID transactionId, UUID userId) {
        Transaction tx = transactionRepository.findByIdAndUserId(transactionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found: " + transactionId));
        transactionRepository.delete(tx);
    }

    public TransactionResponse toResponse(Transaction tx) {
        TransactionResponse.CategoryInfo catInfo = null;
        if (tx.getCategory() != null) {
            Category c = tx.getCategory();
            catInfo = new TransactionResponse.CategoryInfo(c.getId(), c.getName(), c.getIcon(), c.getColor());
        }
        return new TransactionResponse(
                tx.getId(),
                tx.getDescription(),
                tx.getAmount(),
                tx.getCurrency(),
                tx.getTransactionDate(),
                tx.getTransactionType(),
                tx.getMerchant(),
                catInfo,
                tx.isAiCategorized(),
                tx.getAiConfidence(),
                tx.getCategorizationNote(),
                tx.getCreatedAt()
        );
    }

    private String escape(String value) {
        return value.replace("\"", "\"\"");
    }
}
