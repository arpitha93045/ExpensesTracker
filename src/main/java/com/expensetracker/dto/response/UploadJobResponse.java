package com.expensetracker.dto.response;

import com.expensetracker.domain.enums.UploadStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record UploadJobResponse(
        UUID id,
        String fileName,
        String fileType,
        UploadStatus status,
        Integer totalRows,
        int processedRows,
        String errorMessage,
        OffsetDateTime createdAt,
        OffsetDateTime completedAt
) {}
