package com.expensetracker.controller;

import com.expensetracker.domain.entity.UploadJob;
import com.expensetracker.dto.response.UploadJobResponse;
import com.expensetracker.exception.ResourceNotFoundException;
import com.expensetracker.repository.UploadJobRepository;
import com.expensetracker.repository.UserRepository;
import com.expensetracker.service.FileUploadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/uploads")
@RequiredArgsConstructor
@Tag(name = "File Upload", description = "Bank statement upload and status APIs")
public class UploadController {

    private final FileUploadService fileUploadService;
    private final UploadJobRepository uploadJobRepository;
    private final UserRepository userRepository;

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(
        summary = "Upload a bank statement (PDF or CSV). Returns jobId immediately.",
        description = "For password-protected PDFs, pass the password via the optional `pdfPassword` field. " +
                      "It is used only in-memory for decryption and is never stored."
    )
    public Map<String, String> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "pdfPassword", required = false) String pdfPassword,
            @AuthenticationPrincipal UserDetails userDetails) {

        UUID userId = resolveUserId(userDetails);
        UUID jobId = fileUploadService.initiateUpload(file, userId, pdfPassword);
        return Map.of("jobId", jobId.toString(), "message", "File accepted for processing");
    }

    @GetMapping("/{jobId}")
    @Operation(summary = "Poll upload job status")
    public UploadJobResponse getJobStatus(
            @PathVariable UUID jobId,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = resolveUserId(userDetails);
        UploadJob job = uploadJobRepository.findByIdAndUserId(jobId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Upload job not found: " + jobId));
        return toResponse(job);
    }

    private UploadJobResponse toResponse(UploadJob job) {
        return new UploadJobResponse(
                job.getId(),
                job.getFileName(),
                job.getFileType().name(),
                job.getStatus(),
                job.getTotalRows(),
                job.getProcessedRows(),
                job.getErrorMessage(),
                job.getCreatedAt(),
                job.getCompletedAt()
        );
    }

    private UUID resolveUserId(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername())
                .map(u -> u.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}
