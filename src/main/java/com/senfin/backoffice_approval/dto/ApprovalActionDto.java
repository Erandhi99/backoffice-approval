package com.senfin.backoffice_approval.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Payload for a manager rejecting a request. Comment is mandatory so the client
 * always gets a concrete reason. (Approve uses no body -- see controller.) */
public record ApprovalActionDto(
        @NotBlank @Size(max = 1000) String comment
) {}