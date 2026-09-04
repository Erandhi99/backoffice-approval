package com.senfin.backoffice_approval.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/** One fund + amount entry within a client request or manager entry. */
public record FundInvestmentDto(
        @NotNull Long fundId,
        @NotNull @Positive BigDecimal amount
) {}
