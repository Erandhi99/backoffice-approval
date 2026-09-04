package com.senfin.backoffice_approval.repository;

import com.senfin.backoffice_approval.entity.Fund;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FundRepository extends JpaRepository<Fund, Long> {
    Optional<Fund> findBySlug(String slug);
}
