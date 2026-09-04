package com.senfin.backoffice_approval.dto;

/** Read-only view of a fund. */
public record FundDto(
        Long id,
        String name,
        String slug,
        String url
) {}
