package com.senfin.backoffice_approval.dto;

import java.time.Instant;
import java.util.List;

import com.senfin.backoffice_approval.entity.ApprovalStage;
import com.senfin.backoffice_approval.entity.RequestStatus;

public record ClientRequestResponseDto(
        Long id,
        String clientUsername,
        String clientFullName,
        String clientEmail,
        RequestStatus status,
        ApprovalStage currentStage,
        ApprovalStage rejectionStage,
        String rejectionComment,
        List<FundInvestmentDto> fundInvestments,
        Instant createdAt,
        Instant updatedAt,
        List<ApprovalHistoryDto> history,
        /** Non-null only once this request has passed final (MANAGER) approval --
         * a direct, visible signal of "this is now permanently saved," per the
         * requirement that data isn't official until the last checkpoint. */
        Long savedClientId
) {}
