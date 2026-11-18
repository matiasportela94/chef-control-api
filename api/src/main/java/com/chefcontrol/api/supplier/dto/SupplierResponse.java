package com.chefcontrol.api.supplier.dto;

import com.chefcontrol.domain.supplier.ContactInfo;
import com.chefcontrol.domain.supplier.Supplier;

import java.util.UUID;

public record SupplierResponse(
        UUID id,
        String name,
        String phone,
        String email,
        String address,
        String notes,
        String cuit,
        boolean isActive
) {
    public static SupplierResponse from(Supplier supplier) {
        ContactInfo contact = supplier.getContactInfo() != null
                ? supplier.getContactInfo()
                : ContactInfo.empty();
        return new SupplierResponse(
                supplier.getId(),
                supplier.getName(),
                contact.phone(),
                contact.email(),
                contact.address(),
                contact.notes(),
                supplier.getCuit(),
                supplier.isActive());
    }
}
