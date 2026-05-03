package com.expensetracker.service;

import com.expensetracker.domain.entity.Transaction;
import com.expensetracker.domain.entity.UploadJob;
import com.expensetracker.domain.enums.TransactionType;
import com.expensetracker.exception.FileProcessingException;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class PdfParserService {

    // Matches a date at the start of a line: dd-MM-yyyy or dd/MM/yyyy or MM/dd/yyyy or yyyy-MM-dd
    private static final Pattern DATE_LINE_PATTERN = Pattern.compile(
            "^(\\d{1,2}[/\\-]\\d{1,2}[/\\-]\\d{2,4}|\\d{4}[/\\-]\\d{1,2}[/\\-]\\d{1,2})"
    );

    // Matches an amount line: one or two amounts at the end (deposits/withdrawals/balance)
    // e.g. "9,000.00 31,755.32" or just "31,755.32"
    private static final Pattern AMOUNT_LINE_PATTERN = Pattern.compile(
            "(?:([\\d,]+\\.\\d{2})\\s+)?([\\d,]+\\.\\d{2})\\s*$"
    );

    // Header / footer lines to skip
    private static final Pattern SKIP_PATTERN = Pattern.compile(
            "(?i)date|mode|particulars|deposits|withdrawals|balance|page \\d|" +
            "visit www\\.|dial your|base branch|b/f|statement of|account|summary|" +
            "nomination|total|did you know|kyc|relationship|branch"
    );

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("MM/dd/yy"),
            DateTimeFormatter.ofPattern("M/d/yyyy"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd")
    );

    public List<Transaction> parse(UploadJob job) {
        return parse(job, null);
    }

    /**
     * Parses a PDF bank statement, optionally decrypting with the provided password.
     * The password is used purely in-memory and is never stored anywhere.
     */
    public List<Transaction> parse(UploadJob job, String pdfPassword) {
        List<Transaction> transactions = new ArrayList<>();
        try {
            PDDocument doc = openPdf(new File(job.getFilePath()), pdfPassword);
            try {
                PDFTextStripper stripper = new PDFTextStripper();
                String text = stripper.getText(doc);
                log.info("Extracted {} characters from PDF for job {}", text.length(), job.getId());

                transactions = parseMultiLine(text, job);
            } finally {
                doc.close();
            }
        } catch (FileProcessingException e) {
            throw e;
        } catch (Exception e) {
            throw new FileProcessingException("Failed to parse PDF: " + e.getMessage(), e);
        }
        log.info("Parsed {} transactions from PDF job {}", transactions.size(), job.getId());
        return transactions;
    }

    /**
     * Multi-line parser for ICICI / Indian bank statement format:
     *
     * Each transaction block looks like:
     *   06-02-2025
     *   UPI/hemams412018-3@/UPI/CANARA BANK/...
     *   10,000.00 41,755.32
     *
     * Strategy:
     *  - When a date-only line is seen, start accumulating a new transaction block.
     *  - When an amount line is seen while accumulating, close out the block.
     *  - The last amount on an amount line is always the running balance.
     *  - If two amounts appear: first = deposit or withdrawal, second = balance.
     *  - Determine CREDIT vs DEBIT: if only one amount before balance on same line as
     *    previous deposit column position, check context. We use a simpler heuristic:
     *    if balance went up → CREDIT, if balance went down → DEBIT.
     */
    private List<Transaction> parseMultiLine(String text, UploadJob job) {
        List<Transaction> result = new ArrayList<>();
        String[] lines = text.split("\\r?\\n");

        LocalDate currentDate = null;
        List<String> descLines = new ArrayList<>();
        BigDecimal prevBalance = null;

        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty()) continue;
            if (SKIP_PATTERN.matcher(line).find()) continue;

            Matcher dateMatcher = DATE_LINE_PATTERN.matcher(line);
            if (dateMatcher.find()) {
                // Try to parse as a date line
                LocalDate parsedDate = tryParseDate(dateMatcher.group(1));
                if (parsedDate != null) {
                    // Save previous block if any was pending without an amount line
                    currentDate = parsedDate;
                    descLines.clear();
                    // Remainder of this line after the date may be description
                    String remainder = line.substring(dateMatcher.end()).trim();
                    if (!remainder.isEmpty()) {
                        // Check if remainder contains amounts (B/F line)
                        Matcher am = AMOUNT_LINE_PATTERN.matcher(remainder);
                        if (am.find()) {
                            // B/F opening balance line — just capture balance
                            prevBalance = parseBigDecimal(am.group(2));
                        } else {
                            descLines.add(remainder);
                        }
                    }
                    continue;
                }
            }

            if (currentDate == null) continue;

            // Check if this line ends with amounts
            Matcher amountMatcher = AMOUNT_LINE_PATTERN.matcher(line);
            if (amountMatcher.find()) {
                String firstAmtStr = amountMatcher.group(1);  // deposit or withdrawal (may be null)
                String balanceStr = amountMatcher.group(2);   // always running balance

                // Text before the amounts is part of description
                String beforeAmounts = line.substring(0, amountMatcher.start()).trim();
                if (!beforeAmounts.isEmpty()) descLines.add(beforeAmounts);

                BigDecimal balance = parseBigDecimal(balanceStr);
                BigDecimal txAmount;
                TransactionType type;

                if (firstAmtStr != null) {
                    txAmount = parseBigDecimal(firstAmtStr);
                    // Determine type from balance movement
                    if (prevBalance != null) {
                        type = balance.compareTo(prevBalance) >= 0 ? TransactionType.CREDIT : TransactionType.DEBIT;
                    } else {
                        type = TransactionType.DEBIT; // default
                    }
                } else {
                    // Only balance — might be a continuation line, skip
                    prevBalance = balance;
                    descLines.clear();
                    currentDate = null;
                    continue;
                }

                String rawDesc = String.join(" ", descLines).trim();
                String description = buildDescription(descLines);
                if (!description.isEmpty() && txAmount.compareTo(BigDecimal.ZERO) > 0) {
                    result.add(Transaction.builder()
                            .user(job.getUser())
                            .uploadJob(job)
                            .description(description)
                            .amount(txAmount)
                            .transactionDate(currentDate)
                            .transactionType(type)
                            .rawText(rawDesc)
                            .build());
                }

                prevBalance = balance;
                descLines.clear();
                currentDate = null;

            } else {
                // Continuation description line
                descLines.add(line);
            }
        }

        return result;
    }

    private String buildDescription(List<String> lines) {
        if (lines.isEmpty()) return "";
        String raw = String.join(" ", lines).trim();

        // UPI format: UPI/<Merchant Name>/<UPI-ID>/<App>/<Bank>/<txn-ref>/...
        // Segment 1 (index 1) is the merchant/receiver name — always use it first.
        if (raw.toUpperCase().startsWith("UPI/")) {
            String[] parts = raw.split("/");
            String merchant = parts.length > 1 ? parts[1].trim() : "";
            if (!merchant.isBlank() && !merchant.matches("[0-9]+")) {
                raw = merchant;
            } else if (parts.length > 2) {
                // segment 1 was numeric (rare) — fall back to UPI ID prefix before '@'
                String upiId = parts[2].trim();
                raw = upiId.contains("@") ? upiId.split("@")[0].replaceAll("[0-9\\-]+$", "").trim() : upiId;
            }
        }

        raw = raw.replaceAll("(?i)NEFT-[A-Z0-9]+-", "NEFT ");
        raw = raw.replaceAll("(?i)ACH/([^/]+)/.*", "ACH $1");
        raw = raw.replaceAll("\\s{2,}", " ").trim();
        return raw.length() > 255 ? raw.substring(0, 255) : raw;
    }

    private BigDecimal parseBigDecimal(String s) {
        if (s == null) return BigDecimal.ZERO;
        return new BigDecimal(s.replace(",", ""));
    }

    private LocalDate tryParseDate(String dateStr) {
        for (DateTimeFormatter fmt : DATE_FORMATS) {
            try {
                return LocalDate.parse(dateStr, fmt);
            } catch (DateTimeParseException ignored) {
            }
        }
        return null;
    }

    /**
     * Attempts to open a PDF:
     * 1. First tries without a password (handles unprotected and owner-only-locked PDFs).
     * 2. If PDFBox throws InvalidPasswordException, the PDF requires a user password — retry with it.
     * 3. If no password provided for an encrypted PDF, throws a clear user-facing error.
     */
    private PDDocument openPdf(File file, String password) throws java.io.IOException {
        try {
            return Loader.loadPDF(file);
        } catch (org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException e) {
            if (password == null || password.isBlank()) {
                throw new FileProcessingException(
                        "This PDF is password-protected. " +
                        "Please provide the PDF password to proceed.");
            }
            try {
                PDDocument doc = Loader.loadPDF(file, password);
                doc.setAllSecurityToBeRemoved(true);
                return doc;
            } catch (org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException ex) {
                throw new FileProcessingException(
                        "Incorrect PDF password. Please double-check and try again.");
            }
        }
    }
}
