package com.chefcontrol.api.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RenameAccountRequest(
        @NotBlank @Size(max = 255) String name
) {}
