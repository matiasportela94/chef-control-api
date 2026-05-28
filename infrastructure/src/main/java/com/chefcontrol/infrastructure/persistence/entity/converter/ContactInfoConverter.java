package com.chefcontrol.infrastructure.persistence.entity.converter;

import com.chefcontrol.domain.supplier.ContactInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class ContactInfoConverter implements AttributeConverter<ContactInfo, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(ContactInfo attribute) {
        if (attribute == null) return null;
        try {
            return MAPPER.writeValueAsString(attribute);
        } catch (Exception e) {
            return "{}";
        }
    }

    @Override
    public ContactInfo convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) return ContactInfo.empty();
        try {
            return MAPPER.readValue(dbData, ContactInfo.class);
        } catch (Exception e) {
            return ContactInfo.empty();
        }
    }
}
