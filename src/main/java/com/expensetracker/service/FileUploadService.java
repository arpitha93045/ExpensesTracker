package com.expensetracker.service;

import com.expensetracker.domain.entity.Transaction;
import com.expensetracker.domain.entity.UploadJob;
import com.expensetracker.domain.entity.User;
import com.expensetracker.domain.enums.FileType;
import com.expensetracker.domain.enums.UploadStatus;
import com.expensetracker.exception.FileProcessingException;
import com.expensetracker.repository.TransactionRepository;
import com.expensetracker.repository.UploadJobRepository;
import com.expensetracker.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class FileUploadService {

    private final StorageService storageService;
    private final PdfParserService pdfParserService;
    private final CsvParserService csvParserService;
    private final CategorizationService categorizationService;
    private final UploadJobRepository uploadJobRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    /**
     * Saves the file to disk and creates an UploadJob synchronously.
     * Returns the jobId immediately (202 Accepted).
     *
     * @param pdfPassword optional PDF decryption password — used in-memory only, never persisted.
     */
    @Transactional
    public UUID initiateUpload(MultipartFile file, UUID userId, String pdfPassword) {
        User user = userRepository.getReferenceById(userId);
        Path savedPath = storageService.store(file, userId);
        FileType fileType = resolveFileType(file.getOriginalFilename());

        UploadJob job = UploadJob.builder()
                .user(user)
                .fileName(file.getOriginalFilename())
                .filePath(savedPath.toString())
                .fileType(fileType)
                .status(UploadStatus.PENDING)
                .build();
        uploadJobRepository.save(job);
        log.info("Upload job {} created for user {}, file: {}", job.getId(), userId, file.getOriginalFilename());

        processAsync(job.getId(), userId, pdfPassword);
        return job.getId();
    }

    @Async("fileProcessingExecutor")
    public void processAsync(UUID jobId, UUID userId, String pdfPassword) {
        UploadJob job = uploadJobRepository.findById(jobId).orElseThrow();
        try {
            job.setStatus(UploadStatus.PROCESSING);
            uploadJobRepository.save(job);

            List<Transaction> transactions = job.getFileType() == FileType.PDF
                    ? pdfParserService.parse(job, pdfPassword)   // password used here, in-memory only
                    : csvParserService.parse(job);               // CSV never needs a password

            List<Transaction> newTransactions = filterDuplicates(transactions, userId);
            int skipped = transactions.size() - newTransactions.size();
            if (skipped > 0) {
                log.info("Job {}: skipped {} duplicate transaction(s)", jobId, skipped);
            }

            job.setTotalRows(newTransactions.size());
            transactionRepository.saveAll(newTransactions);
            uploadJobRepository.save(job);

            log.info("Parsed {} transactions for job {}, starting categorization", newTransactions.size(), jobId);
            categorizationService.categorizeTransactions(newTransactions, userId);

            job.setStatus(UploadStatus.COMPLETED);
            job.setProcessedRows(newTransactions.size());
            job.setCompletedAt(OffsetDateTime.now());
            uploadJobRepository.save(job);
            log.info("Job {} completed successfully with {} transactions", jobId, transactions.size());

            User user = userRepository.findById(userId).orElse(null);
            if (user != null) {
                notificationService.sendUploadComplete(
                        user.getEmail(), user.getFullName(),
                        job.getFileName(), newTransactions.size(), true, null,
                        user.isNotificationsEnabled());
            }

        } catch (Exception ex) {
            log.error("Job {} failed: {}", jobId, ex.getMessage(), ex);
            job.setStatus(UploadStatus.FAILED);
            job.setErrorMessage(ex.getMessage());
            uploadJobRepository.save(job);

            User user = userRepository.findById(userId).orElse(null);
            if (user != null) {
                notificationService.sendUploadComplete(
                        user.getEmail(), user.getFullName(),
                        job.getFileName(), 0, false, ex.getMessage(),
                        user.isNotificationsEnabled());
            }
        }
    }

    /**
     * Filters out transactions that already exist in the database for this user,
     * matched by (transactionDate, amount, transactionType, description).
     */
    private List<Transaction> filterDuplicates(List<Transaction> incoming, UUID userId) {
        if (incoming.isEmpty()) return incoming;

        // Build a Postgres array literal of the distinct dates to narrow the lookup
        String datesArray = incoming.stream()
                .map(t -> t.getTransactionDate().toString())
                .distinct()
                .collect(Collectors.joining(",", "{", "}"));

        Set<String> existingKeys = new java.util.HashSet<>(
                transactionRepository.findExistingKeys(userId, datesArray));

        return incoming.stream()
                .filter(t -> {
                    String key = t.getTransactionDate() + "|" + t.getAmount() + "|"
                            + t.getTransactionType() + "|" + t.getDescription();
                    return existingKeys.add(key); // add returns false if already present
                })
                .collect(Collectors.toList());
    }

    private FileType resolveFileType(String filename) {
        if (filename == null) throw new FileProcessingException("Filename is missing");
        String lower = filename.toLowerCase();
        if (lower.endsWith(".pdf")) return FileType.PDF;
        if (lower.endsWith(".csv")) return FileType.CSV;
        throw new FileProcessingException("Unsupported file type. Only PDF and CSV are accepted.");
    }
}
