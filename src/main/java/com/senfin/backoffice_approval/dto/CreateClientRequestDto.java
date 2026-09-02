package com.senfin.backoffice_approval.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** Payload the client submits to start (or edit + resubmit) an onboarding request. */
public record CreateClientRequestDto(
        @NotBlank @Size(max = 150) String name,
        @NotBlank @Size(max = 20) String nic,
        @NotBlank @Size(max = 300) String address,
        @NotNull @Past LocalDate dateOfBirth
) {}