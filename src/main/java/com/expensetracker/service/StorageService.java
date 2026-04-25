package com.expensetracker.service;

import com.expensetracker.exception.FileProcessingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
public class StorageService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "application/pdf", "text/csv", "text/plain", "application/vnd.ms-excel"
    );
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(".pdf", ".csv");

    private final Path baseStoragePath;

    public StorageService(@Value("${app.storage.base-path:./uploads}") String basePath) {
        this.baseStoragePath = Paths.get(basePath).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.baseStoragePath);
        } catch (IOException e) {
            throw new IllegalStateException("Could not initialize storage location: " + basePath, e);
        }
        log.info("Storage initialized at: {}", this.baseStoragePath);
    }

    public Path store(MultipartFile file, UUID userId) {
        String originalFilename = file.getOriginalFilename();
        validateFile(file, originalFilename);

        String extension = getExtension(originalFilename);
        String storedName = UUID.randomUUID() + extension;

        Path userDir = baseStoragePath.resolve(userId.toString());
        try {
            Files.createDirectories(userDir);
            Path destination = userDir.resolve(storedName).normalize();

            // Prevent path traversal
            if (!destination.startsWith(userDir)) {
                throw new FileProcessingException("Cannot store file outside designated directory");
            }

            Files.copy(file.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);
            log.info("Stored file {} for user {}", storedName, userId);
            return destination;
        } catch (IOException e) {
            throw new FileProcessingException("Failed to store file: " + e.getMessage(), e);
        }
    }

    private void validateFile(MultipartFile file, String filename) {
        if (file.isEmpty()) throw new FileProcessingException("Cannot upload empty file");
        if (filename == null || filename.isBlank()) throw new FileProcessingException("Filename is missing");

        String extension = getExtension(filename).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new FileProcessingException("Only PDF and CSV files are allowed");
        }
        if (file.getContentType() != null && !ALLOWED_CONTENT_TYPES.contains(file.getContentType())) {
            log.warn("Unexpected content type {} for file {}", file.getContentType(), filename);
        }
    }

    private String getExtension(String filename) {
        if (filename == null) return "";
        int dotIndex = filename.lastIndexOf('.');
        return dotIndex >= 0 ? filename.substring(dotIndex) : "";
    }
}
