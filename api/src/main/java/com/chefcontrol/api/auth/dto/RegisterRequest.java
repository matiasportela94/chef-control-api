package com.chefcontrol.api.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank String restaurantName,
        @NotBlank String ownerName,
        @NotBlank @Email String ownerEmail,
        @NotBlank @Size(min = 8, max = 100) String ownerPassword,
        String ownerPhone,
        String timezone
) {}
