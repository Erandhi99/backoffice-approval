package com.senfin.backoffice_approval.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * Payload the client submits to start (or edit + resubmit) an investment request.
 * Personal details are auto-retrieved from the User account; only fund investment
 * details are needed from the client.
 */
public record CreateClientRequestDto(
        @NotEmpty List<FundInvestmentDto> fundInvestments
) {}