package com.chefcontrol.api.profile.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** @param phone opcional; vacío = sin teléfono */
public record UpdateProfileRequest(@NotBlank @Size(max = 255) String name, @Size(max = 20) String phone) {}
