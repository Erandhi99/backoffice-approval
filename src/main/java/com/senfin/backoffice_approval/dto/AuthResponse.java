package com.senfin.backoffice_approval.dto;

import com.senfin.backoffice_approval.entity.Role;

public record AuthResponse(
        String token,
        String username,
        Role role
) {}