package com.chefcontrol.api.supplier.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateSupplierRequest(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 20) String phone,
        @Email @Size(max = 255) String email,
        @Size(max = 500) String address,
        @Size(max = 1000) String notes
) {}
