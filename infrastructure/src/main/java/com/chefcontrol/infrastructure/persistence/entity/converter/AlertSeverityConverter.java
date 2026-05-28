package com.chefcontrol.infrastructure.persistence.entity.converter;

import com.chefcontrol.domain.alert.AlertSeverity;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class AlertSeverityConverter implements AttributeConverter<AlertSeverity, String> {

    @Override
    public String convertToDatabaseColumn(AlertSeverity attribute) {
        return attribute == null ? null : attribute.name().toLowerCase();
    }

    @Override
    public AlertSeverity convertToEntityAttribute(String dbData) {
        return dbData == null ? null : AlertSeverity.valueOf(dbData.toUpperCase());
    }
}
