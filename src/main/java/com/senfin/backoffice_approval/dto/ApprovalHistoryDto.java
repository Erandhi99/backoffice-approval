package com.senfin.backoffice_approval.dto;

import com.senfin.backoffice_approval.entity.ApprovalStage;
import com.senfin.backoffice_approval.entity.HistoryAction;

import java.time.Instant;

public record ApprovalHistoryDto(
        HistoryAction action,
        ApprovalStage stage,
        String performedByUsername,
        String performedByRole,
        String comment,
        Instant timestamp
) {}