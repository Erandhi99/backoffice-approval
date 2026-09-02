package com.senfin.backoffice_approval.dto;

import com.senfin.backoffice_approval.entity.ApprovalStage;
import com.senfin.backoffice_approval.entity.RequestStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record ClientRequestResponseDto(
        Long id,
        String clientUsername,
        String name,
        String nic,
        String address,
        LocalDate dateOfBirth,
        RequestStatus status,
        ApprovalStage currentStage,
        ApprovalStage rejectionStage,
        String rejectionComment,
        Instant createdAt,
        Instant updatedAt,
        List<ApprovalHistoryDto> history
) {}