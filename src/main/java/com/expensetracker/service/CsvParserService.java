package com.expensetracker.service;

import com.expensetracker.domain.entity.Transaction;
import com.expensetracker.domain.entity.UploadJob;
import com.expensetracker.domain.enums.TransactionType;
import com.expensetracker.exception.FileProcessingException;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.FileReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class CsvParserService {

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("MM-dd-yyyy"),
            DateTimeFormatter.ofPattern("M/d/yyyy")
    );

    public List<Transaction> parse(UploadJob job) {
        List<Transaction> transactions = new ArrayList<>();

        try (CSVReader reader = new CSVReader(new FileReader(job.getFilePath()))) {
            String[] header = reader.readNext();
            if (header == null) throw new FileProcessingException("CSV file is empty");

            int[] indices = resolveColumnIndices(header);
            String[] row;
            int lineNum = 1;

            while ((row = reader.readNext()) != null) {
                lineNum++;
                try {
                    Transaction tx = parseRow(row, indices, job);
                    if (tx != null) transactions.add(tx);
                } catch (Exception e) {
                    log.warn("Skipping CSV row {}: {}", lineNum, e.getMessage());
                }
            }
        } catch (IOException | CsvValidationException e) {
            throw new FileProcessingException("Failed to parse CSV: " + e.getMessage(), e);
        }

        log.info("Parsed {} transactions from CSV job {}", transactions.size(), job.getId());
        return transactions;
    }

    private int[] resolveColumnIndices(String[] header) {
        int dateIdx = -1, descIdx = -1, amountIdx = -1, typeIdx = -1, merchantIdx = -1;

        for (int i = 0; i < header.length; i++) {
            String col = header[i].trim().toLowerCase();
            if (col.contains("date"))                               dateIdx = i;
            else if (col.contains("desc") || col.contains("narr") || col.contains("memo")) descIdx = i;
            else if (col.contains("amount") || col.equals("debit") || col.equals("credit")) amountIdx = i;
            else if (col.contains("type") || col.contains("dr/cr")) typeIdx = i;
            else if (col.contains("merchant") || col.contains("payee")) merchantIdx = i;
        }

        if (dateIdx < 0 || descIdx < 0 || amountIdx < 0) {
            throw new FileProcessingException(
                    "CSV must have columns: Date, Description/Narration, Amount. Found: " +
                    String.join(", ", header));
        }
        return new int[]{dateIdx, descIdx, amountIdx, typeIdx, merchantIdx};
    }

    private Transaction parseRow(String[] row, int[] indices, UploadJob job) {
        int dateIdx = indices[0], descIdx = indices[1], amountIdx = indices[2];
        int typeIdx = indices[3], merchantIdx = indices[4];

        if (row.length <= amountIdx) return null;

        String rawAmount = row[amountIdx].trim().replaceAll("[,$]", "");
        if (rawAmount.isBlank()) return null;

        BigDecimal amount = new BigDecimal(rawAmount.replace("(", "-").replace(")", ""));
        TransactionType type = amount.compareTo(BigDecimal.ZERO) < 0 ? TransactionType.CREDIT : TransactionType.DEBIT;

        if (typeIdx >= 0 && typeIdx < row.length) {
            String typeStr = row[typeIdx].trim().toUpperCase();
            if (typeStr.contains("CR") || typeStr.contains("CREDIT")) type = TransactionType.CREDIT;
            else if (typeStr.contains("DR") || typeStr.contains("DEBIT")) type = TransactionType.DEBIT;
        }

        return Transaction.builder()
                .user(job.getUser())
                .uploadJob(job)
                .description(row[descIdx].trim())
                .amount(amount.abs())
                .transactionDate(parseDate(row[dateIdx].trim()))
                .transactionType(type)
                .merchant(merchantIdx >= 0 && merchantIdx < row.length ? row[merchantIdx].trim() : null)
                .rawText(String.join(",", row))
                .build();
    }

    private LocalDate parseDate(String dateStr) {
        for (DateTimeFormatter fmt : DATE_FORMATS) {
            try {
                return LocalDate.parse(dateStr, fmt);
            } catch (DateTimeParseException ignored) {
                // try next
            }
        }
        throw new IllegalArgumentException("Cannot parse date: " + dateStr);
    }
}
