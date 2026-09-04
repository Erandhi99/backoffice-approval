package com.senfin.backoffice_approval.dto;

import java.math.BigDecimal;

/** Shows one permanent fund investment of an approved client. */
public record ClientFundInvestmentDto(
        Long id,
        Long fundId,
        String fundName,
        String fundSlug,
        BigDecimal amount,
        Long sourceRequestId
) {}
