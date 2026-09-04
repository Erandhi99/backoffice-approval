package com.senfin.backoffice_approval.dto;

import java.time.Instant;
import java.time.LocalDate;

public record ClientResponseDto(
        Long id,
        String name,
        String nic,
        String address,
        LocalDate dateOfBirth,
        Long sourceRequestId,
        Instant approvedAt
) {}