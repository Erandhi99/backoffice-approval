package com.senfin.backoffice_approval.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record ClientResponseDto(
        Long id,
        Long userId,
        String name,
        String nic,
        String address,
        LocalDate dateOfBirth,
        List<ClientFundInvestmentDto> fundInvestments,
        Instant approvedAt
) {}