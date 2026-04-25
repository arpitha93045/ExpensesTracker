package com.expensetracker.repository;

import com.expensetracker.domain.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryRepository extends JpaRepository<Category, Integer> {

    @Query("SELECT c FROM Category c WHERE c.user IS NULL OR c.user.id = :userId ORDER BY c.name")
    List<Category> findAllForUser(@Param("userId") UUID userId);

    Optional<Category> findByNameIgnoreCaseAndUserIsNull(String name);
}
