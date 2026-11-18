package com.chefcontrol.api.supplier.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateSupplierRequest(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 20) String phone,
        @Email @Size(max = 255) String email,
        @Size(max = 500) String address,
        @Size(max = 1000) String notes,
        /** Con o sin guiones; se valida el dígito verificador (INVALID_CUIT) y que no lo tenga otro proveedor activo. */
        @Size(max = 13) String cuit
) {}
