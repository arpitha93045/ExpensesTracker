package com.expensetracker.repository;

import com.expensetracker.domain.entity.UploadJob;
import com.expensetracker.domain.enums.UploadStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UploadJobRepository extends JpaRepository<UploadJob, UUID> {

    Page<UploadJob> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Optional<UploadJob> findByIdAndUserId(UUID id, UUID userId);
}
