package com.chefcontrol.domain.supplier;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ContactInfo(
        String phone,
        String email,
        String address,
        String notes
) {
    public static ContactInfo empty() {
        return new ContactInfo(null, null, null, null);
    }
}
