package com.senfin.backoffice_approval.repository;

import com.senfin.backoffice_approval.entity.ApprovalHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApprovalHistoryRepository extends JpaRepository<ApprovalHistory, Long> {
    List<ApprovalHistory> findByRequestIdOrderByTimestampAsc(Long requestId);
}