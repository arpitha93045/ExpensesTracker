package com.expensetracker.repository;

import com.expensetracker.domain.entity.TwoFactorChallenge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface TwoFactorChallengeRepository extends JpaRepository<TwoFactorChallenge, UUID> {

    Optional<TwoFactorChallenge> findByIdAndUsedFalse(UUID id);

    @Modifying
    @Query("DELETE FROM TwoFactorChallenge c WHERE c.user.id = :userId AND c.used = false")
    void deleteByUserIdAndUsedFalse(UUID userId);
}
