package com.senfin.backoffice_approval.dto;

import com.senfin.backoffice_approval.entity.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * NOTE (best practice): in a real deployment this endpoint would be locked down
 * to admins only (staff accounts are provisioned, not self-registered), and
 * CLIENT sign-up would be a separate, unauthenticated flow. It's left open here
 * for local development convenience -- see README "Security hardening" section.
 */
public record RegisterRequest(
        @NotBlank @Size(min = 4, max = 50) String username,
        @NotBlank @Size(min = 6, max = 100) String password,
        @NotBlank @Size(max = 150) String fullName,
        @NotBlank @Email @Size(max = 150) String email,
        @NotNull Role role
) {}